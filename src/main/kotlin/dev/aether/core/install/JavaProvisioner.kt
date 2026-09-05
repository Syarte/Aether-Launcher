package dev.aether.core.install

import dev.aether.core.Platform
import dev.aether.core.meta.MetaClient
import dev.aether.core.net.DownloadTask
import dev.aether.core.net.Downloader
import dev.aether.core.net.Progress
import java.io.File

/**
 * Подбор JRE под версию игры.
 *
 * Приоритет: рантайм Mojang для компонента из `javaVersion.component`
 * (jre-legacy / java-runtime-alpha|beta|gamma|delta) -> подходящая
 * локальная Java -> явно указанный пользователем путь.
 */
class JavaProvisioner(private val meta: MetaClient, private val runtimesDir: File) {

    suspend fun provide(
        component: String,
        majorVersion: Int,
        override: File?,
        onProgress: (Progress) -> Unit,
    ): File {
        override?.let { if (it.canExecute()) return it }

        val installed = File(runtimesDir, "$component/${Platform.runtimeKey}")
        executableIn(installed)?.let { return it }

        runCatching { return download(component, installed, onProgress) }
            .onFailure { failure ->
                localJava(majorVersion)?.let { return it }
                throw IllegalStateException(
                    "Не удалось получить Java $majorVersion для этой версии игры: ${failure.message}"
                )
            }
        error("unreachable")
    }

    private suspend fun download(component: String, target: File, onProgress: (Progress) -> Unit): File {
        val all = meta.javaRuntimes()
        val entry = all[Platform.runtimeKey]?.get(component)?.firstOrNull()
            ?: error("Mojang не публикует $component для платформы ${Platform.runtimeKey}")

        val manifest = meta.javaRuntimeManifest(entry.manifest.url)
        val tasks = mutableListOf<DownloadTask>()
        val links = mutableListOf<Pair<File, String>>()

        manifest.files.forEach { (path, file) ->
            val out = File(target, path)
            when (file.type) {
                "directory" -> out.mkdirs()
                "link" -> file.target?.let { links += out to it }
                "file" -> {
                    val raw = file.downloads?.get("raw") ?: return@forEach
                    tasks += DownloadTask(raw.url, out, raw.sha1, raw.size, executable = file.executable)
                }
            }
        }
        Downloader.downloadAll(tasks, "Установка Java", onProgress)
        links.forEach { (link, to) ->
            runCatching {
                link.parentFile.mkdirs()
                java.nio.file.Files.deleteIfExists(link.toPath())
                java.nio.file.Files.createSymbolicLink(link.toPath(), File(to).toPath())
            }
        }
        return executableIn(target) ?: error("В рантайме $component не найден исполняемый java")
    }

    private fun executableIn(dir: File): File? {
        if (!dir.isDirectory) return null
        val candidates = listOf(
            "bin/java", "bin/java.exe",
            "jre.bundle/Contents/Home/bin/java",   // раскладка Mojang на macOS
        )
        return candidates.map { File(dir, it) }.firstOrNull { it.isFile && it.canExecute() }
    }

    /** Фолбэк: JAVA_HOME или java в PATH, если мажорная версия подходит. */
    private fun localJava(majorVersion: Int): File? {
        val candidates = buildList {
            System.getenv("JAVA_HOME")?.let { add(File(it, if (Platform.isWindows) "bin/java.exe" else "bin/java")) }
            add(File(System.getProperty("java.home"), if (Platform.isWindows) "bin/java.exe" else "bin/java"))
        }
        return candidates.firstOrNull { it.isFile && majorOf(it)?.let { m -> m >= majorVersion } == true }
    }

    private fun majorOf(java: File): Int? = runCatching {
        val process = ProcessBuilder(java.absolutePath, "-version").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        Regex("""version "(\d+)""").find(output)?.groupValues?.get(1)?.toInt()
    }.getOrNull()
}
