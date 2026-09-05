package dev.aether.core.loader

import dev.aether.core.install.GamePaths
import dev.aether.core.install.Rules
import dev.aether.core.meta.Library
import dev.aether.core.net.DownloadTask
import dev.aether.core.net.Downloader
import dev.aether.core.net.Http
import dev.aether.core.net.Progress
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.io.File
import java.util.jar.JarFile
import java.util.zip.ZipFile

/**
 * Forge и NeoForge. Отличаются только координатами Maven и схемой версий,
 * формат инсталлятора у них общий, поэтому реализация одна.
 *
 *   Forge:    https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml
 *             .../forge/<mc>-<forge>/forge-<mc>-<forge>-installer.jar
 *   NeoForge: https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml
 *             .../neoforge/<ver>/neoforge-<ver>-installer.jar
 *
 * Ничего проприетарного не распространяется: инсталлятор скачивается
 * с официального Maven, клиент патчится локально из легально скачанного
 * ванильного jar.
 */
class ForgeProvider(override val loader: Loader) : LoaderProvider {

    private val mavenRoot = when (loader) {
        Loader.FORGE -> "https://maven.minecraftforge.net"
        Loader.NEOFORGE -> "https://maven.neoforged.net/releases"
        else -> error("ForgeProvider поддерживает только FORGE и NEOFORGE")
    }

    private val groupPath = when (loader) {
        Loader.FORGE -> "net/minecraftforge/forge"
        else -> "net/neoforged/neoforge"
    }

    override suspend fun availableVersions(gameVersion: String): List<LoaderVersion> {
        val url = "$mavenRoot/$groupPath/maven-metadata.xml"
        Http.assertAllowed(url)
        val response = Http.client.get(url)
        if (response.status.value !in 200..299) return emptyList()
        val all = Regex("<version>([^<]+)</version>").findAll(response.bodyAsText()).map { it.groupValues[1] }.toList()

        val matching = when (loader) {
            Loader.FORGE -> all.filter { it.startsWith("$gameVersion-") }.map { it.removePrefix("$gameVersion-") }
            // NeoForge нумеруется от версии игры: 1.21.1 -> 21.1.x, 1.21 -> 21.0.x
            else -> {
                val prefix = neoForgePrefix(gameVersion) ?: return emptyList()
                all.filter { it.startsWith("$prefix.") }
            }
        }
        return matching.reversed().mapIndexed { index, version ->
            LoaderVersion(loader, version, recommended = index == 0, stable = !version.contains("beta"))
        }
    }

    private fun neoForgePrefix(gameVersion: String): String? {
        val parts = gameVersion.split(".")
        if (parts.size < 2 || parts[0] != "1") return null
        val minor = parts[1]
        val patch = parts.getOrNull(2) ?: "0"
        return "$minor.$patch"
    }

    private fun artifactVersion(gameVersion: String, loaderVersion: String) =
        if (loader == Loader.FORGE) "$gameVersion-$loaderVersion" else loaderVersion

    private fun artifactName(gameVersion: String, loaderVersion: String) =
        if (loader == Loader.FORGE) "forge-${artifactVersion(gameVersion, loaderVersion)}"
        else "neoforge-$loaderVersion"

    override suspend fun install(
        gameVersion: String,
        loaderVersion: String,
        paths: GamePaths,
        java: File,
        onProgress: (Progress) -> Unit,
    ): String {
        val version = artifactVersion(gameVersion, loaderVersion)
        val name = artifactName(gameVersion, loaderVersion)
        val installerUrl = "$mavenRoot/$groupPath/$version/$name-installer.jar"

        onProgress(Progress("Установка ${loader.displayName}", 0, 4))

        val work = File(paths.root, "installers/${loader.name.lowercase()}-$version")
        work.mkdirs()
        val installerJar = File(work, "installer.jar")
        Downloader.download(DownloadTask(installerUrl, installerJar, sha1 = fetchSha1("$installerUrl.sha1")))

        // --- 1. Достаём профиль запуска и профиль установки ---
        val versionJsonText = readFromJar(installerJar, "version.json")
            ?: error("В инсталляторе ${loader.displayName} нет version.json")
        val installProfileText = readFromJar(installerJar, "install_profile.json")
            ?: error("В инсталляторе ${loader.displayName} нет install_profile.json")

        val versionId = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(versionJsonText)?.groupValues?.get(1)
            ?: error("В version.json нет поля id")
        val profileVersionFile = File(paths.versionDir(versionId), "$versionId.json")
        profileVersionFile.parentFile.mkdirs()
        profileVersionFile.writeText(versionJsonText)

        val profile = Http.json.decodeFromString(InstallProfile.serializer(), installProfileText)
        onProgress(Progress("Установка ${loader.displayName}", 1, 4))

        // --- 2. Библиотеки, нужные самим процессорам ---
        val installerLibs = profile.libraries.filter { Rules.allows(it.rules) }
        Downloader.downloadAll(
            installerLibs.mapNotNull { library ->
                val artifact = library.downloads?.artifact ?: return@mapNotNull null
                DownloadTask(artifact.url, Rules.libraryFile(paths.libraries, library), artifact.sha1, artifact.size)
            },
            "Установка ${loader.displayName}",
            onProgress,
        )

        // --- 3. Разворачиваем ссылки на данные (клиентская сторона) ---
        val dataDir = File(work, "data").apply { mkdirs() }
        val tokens = resolveData(profile, installerJar, dataDir, paths, gameVersion, work)
        onProgress(Progress("Установка ${loader.displayName}", 2, 4))

        // --- 4. Прогоняем процессоры: патч и деобфускация клиента ---
        val runner = ProcessorRunner(java, paths.libraries)
        val clientProcessors = profile.processors.filter { it.sides.isEmpty() || "client" in it.sides }
        clientProcessors.forEachIndexed { index, processor ->
            onProgress(
                Progress(
                    "Установка ${loader.displayName}",
                    index.toLong(),
                    clientProcessors.size.toLong(),
                    "процессор ${index + 1}",
                )
            )
            runner.run(processor, tokens)
        }

        onProgress(Progress("Установка ${loader.displayName}", 4, 4))
        return versionId
    }

    /**
     * Значения в `data` бывают трёх видов:
     *  `[group:artifact:ver:classifier]` — путь в локальном Maven-репозитории,
     *  `/path/in/installer`             — файл внутри jar инсталлятора,
     *  всё остальное                    — литерал.
     * Плюс несколько служебных токенов, которые ожидают процессоры.
     */
    private fun resolveData(
        profile: InstallProfile,
        installerJar: File,
        dataDir: File,
        paths: GamePaths,
        gameVersion: String,
        work: File,
    ): Map<String, String> {
        val tokens = mutableMapOf<String, String>()

        profile.data.forEach { (key, entry) ->
            val raw = entry.client
            tokens[key] = when {
                raw.startsWith("[") && raw.endsWith("]") ->
                    File(paths.libraries, Rules.mavenPath(raw.trim('[', ']'))).absolutePath

                raw.startsWith("/") -> {
                    val out = File(dataDir, raw.trimStart('/').replace('/', '_'))
                    extractFromJar(installerJar, raw.trimStart('/'), out)
                    out.absolutePath
                }

                else -> raw
            }
        }

        tokens["SIDE"] = "client"
        tokens["MINECRAFT_JAR"] = paths.clientJar(gameVersion).absolutePath
        tokens["MINECRAFT_VERSION"] = gameVersion
        tokens["ROOT"] = paths.root.absolutePath
        tokens["INSTALLER"] = installerJar.absolutePath
        tokens["LIBRARY_DIR"] = paths.libraries.absolutePath
        return tokens
    }

    private suspend fun fetchSha1(url: String): String? = runCatching {
        Http.assertAllowed(url)
        val response = Http.client.get(url)
        if (response.status.value !in 200..299) null
        else response.bodyAsText().trim().substringBefore(' ').takeIf { it.length == 40 }
    }.getOrNull()

    private fun readFromJar(jar: File, entry: String): String? =
        ZipFile(jar).use { zip -> zip.getEntry(entry)?.let { zip.getInputStream(it).bufferedReader().readText() } }

    private fun extractFromJar(jar: File, entry: String, target: File) {
        ZipFile(jar).use { zip ->
            val e = zip.getEntry(entry) ?: error("В инсталляторе нет файла $entry")
            target.parentFile.mkdirs()
            zip.getInputStream(e).use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }
}
