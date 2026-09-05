package dev.aether.core.install

import dev.aether.core.Platform
import dev.aether.core.meta.*
import dev.aether.core.net.DownloadTask
import dev.aether.core.net.Downloader
import dev.aether.core.net.Progress
import java.io.File
import java.util.zip.ZipFile

/** Разложение каталога данных игры. Совместимо с раскладкой официального лаунчера. */
class GamePaths(val root: File) {
    val versions = File(root, "versions")
    val libraries = File(root, "libraries")
    val assets = File(root, "assets")
    val runtimes = File(root, "runtimes")
    val natives = File(root, "natives")
    fun versionDir(id: String) = File(versions, id)
    fun clientJar(id: String) = File(versionDir(id), "$id.jar")
    fun nativesDir(id: String) = File(natives, id)
}

/**
 * Установка ванильного клиента строго по официальной схеме:
 * client.jar -> библиотеки (с учётом rules) -> natives -> ассеты.
 * Каждый файл проверяется по SHA-1 из метаданных Mojang.
 */
class GameInstaller(private val meta: MetaClient, private val paths: GamePaths) {

    suspend fun install(
        version: VersionJson,
        onProgress: (Progress) -> Unit,
    ): InstalledGame {
        val libraries = version.libraries.filter { Rules.allows(it.rules) }

        // --- 1. Клиентский JAR ---
        onProgress(Progress("Проверка файлов", 0, 1))
        version.downloads?.client?.let { client ->
            val jar = paths.clientJar(version.id)
            val task = DownloadTask(client.url, jar, client.sha1, client.size)
            if (!Downloader.isValid(task)) Downloader.download(task)
        }

        // --- 2. Библиотеки ---
        val libraryTasks = mutableListOf<DownloadTask>()
        val classpath = mutableListOf<File>()
        val nativeJars = mutableListOf<Pair<File, ExtractRule?>>()

        for (library in libraries) {
            val legacyClassifier = Rules.nativeClassifier(library)

            if (legacyClassifier != null) {
                // Формат до 1.19: отдельный jar с natives, в classpath не попадает.
                val artifact = library.downloads?.classifiers?.get(legacyClassifier)
                if (artifact != null) {
                    val file = File(paths.libraries, artifact.path ?: Rules.mavenPath("${library.name}:$legacyClassifier"))
                    libraryTasks += DownloadTask(artifact.url, file, artifact.sha1, artifact.size)
                    nativeJars += file to library.extract
                }
                // У некоторых записей есть и обычный артефакт — он идёт в classpath.
                library.downloads?.artifact?.let { artifact ->
                    val file = Rules.libraryFile(paths.libraries, library)
                    libraryTasks += DownloadTask(artifact.url, file, artifact.sha1, artifact.size)
                    classpath += file
                }
                continue
            }

            val file = Rules.libraryFile(paths.libraries, library)
            classpath += file
            val artifact = library.downloads?.artifact
            if (artifact != null) {
                libraryTasks += DownloadTask(artifact.url, file, artifact.sha1, artifact.size)
            } else if (library.url != null) {
                // Библиотеки модлоадеров описываются только Maven-корнем.
                libraryTasks += DownloadTask(library.url.trimEnd('/') + "/" + Rules.mavenPath(library.name), file)
            } else {
                libraryTasks += DownloadTask("${MetaClient.LIBRARIES}/${Rules.mavenPath(library.name)}", file)
            }
        }

        Downloader.downloadAll(libraryTasks, "Загрузка библиотек", onProgress)

        // --- 3. Распаковка natives ---
        val nativesDir = paths.nativesDir(version.id)
        if (nativeJars.isNotEmpty()) {
            nativesDir.mkdirs()
            nativeJars.forEach { (jar, rule) -> extractNatives(jar, nativesDir, rule) }
        }

        // --- 4. Ассеты ---
        val assetIndexRef = version.assetIndex
        var virtualDir: File? = null
        if (assetIndexRef != null) {
            val index = meta.assetIndex(assetIndexRef, paths.assets)
            val objectsDir = File(paths.assets, "objects")
            val assetTasks = index.objects.values.distinctBy { it.hash }.map { obj ->
                DownloadTask(
                    url = "${MetaClient.RESOURCES}/${obj.path}",
                    target = File(objectsDir, obj.path),
                    sha1 = obj.hash,
                    size = obj.size,
                )
            }
            Downloader.downloadAll(assetTasks, "Загрузка ассетов", onProgress)

            // Старые версии читают ассеты по человекочитаемым именам.
            if (index.virtual || index.mapToResources) {
                val target = if (index.mapToResources) File(paths.root, "resources")
                else File(paths.assets, "virtual/${assetIndexRef.id}")
                materialize(index, objectsDir, target)
                if (index.virtual) virtualDir = target
            }
        }

        // --- 5. Конфигурация логгера (log4j2.xml) ---
        var loggingConfig: File? = null
        version.logging?.client?.let { logging ->
            val file = File(paths.assets, "log_configs/${logging.file.id}")
            val task = DownloadTask(logging.file.url, file, logging.file.sha1, logging.file.size)
            if (!Downloader.isValid(task)) Downloader.download(task)
            loggingConfig = file
        }

        return InstalledGame(
            version = version,
            clientJar = paths.clientJar(version.id),
            classpath = classpath,
            nativesDir = nativesDir,
            assetsDir = paths.assets,
            virtualAssetsDir = virtualDir,
            loggingConfig = loggingConfig,
        )
    }

    private fun extractNatives(jar: File, target: File, rule: ExtractRule?) {
        if (!jar.isFile) return
        val excludes = rule?.exclude ?: listOf("META-INF/")
        ZipFile(jar).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                if (excludes.any { entry.name.startsWith(it) }) continue
                // Защита от path traversal в архиве.
                val out = File(target, entry.name).canonicalFile
                if (!out.path.startsWith(target.canonicalFile.path)) continue
                out.parentFile.mkdirs()
                zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                if (!Platform.isWindows) out.setExecutable(true, false)
            }
        }
    }

    private fun materialize(index: AssetIndex, objectsDir: File, target: File) {
        index.objects.forEach { (name, obj) ->
            val src = File(objectsDir, obj.path)
            val dst = File(target, name)
            if (src.isFile && (!dst.isFile || dst.length() != src.length())) {
                dst.parentFile.mkdirs()
                src.copyTo(dst, overwrite = true)
            }
        }
    }
}

data class InstalledGame(
    val version: VersionJson,
    val clientJar: File,
    val classpath: List<File>,
    val nativesDir: File,
    val assetsDir: File,
    val virtualAssetsDir: File?,
    val loggingConfig: File?,
)
