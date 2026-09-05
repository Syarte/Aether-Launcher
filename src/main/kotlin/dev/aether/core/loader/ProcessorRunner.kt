package dev.aether.core.loader

import dev.aether.core.Platform
import dev.aether.core.install.Rules
import dev.aether.core.net.Downloader
import java.io.File
import java.util.jar.JarFile

/**
 * Запуск процессоров инсталлятора Forge/NeoForge.
 *
 * Каждый процессор — обычный jar с Main-Class в манифесте. Мы собираем
 * classpath, подставляем токены в аргументы и запускаем отдельный процесс
 * той же Java, что будет запускать игру. Если все файлы из `outputs` уже
 * лежат на диске с нужным SHA-1, шаг пропускается — повторная установка
 * той же сборки проходит мгновенно.
 */
class ProcessorRunner(private val java: File, private val librariesDir: File) {

    fun run(processor: Processor, tokens: Map<String, String>) {
        val outputs = processor.outputs.mapKeys { substitute(it.key, tokens) }
            .mapValues { substitute(it.value, tokens) }

        if (outputs.isNotEmpty() && outputs.all { (path, sha1) ->
                val file = File(path)
                file.isFile && (sha1.isBlank() || Downloader.sha1(file).equals(sha1.trim('\''), true))
            }
        ) return

        val jar = File(librariesDir, Rules.mavenPath(processor.jar))
        require(jar.isFile) { "Не найден процессор ${processor.jar}" }

        val classpath = (listOf(jar) + processor.classpath.map { File(librariesDir, Rules.mavenPath(it)) })
            .joinToString(Platform.classpathSeparator) { it.absolutePath }

        val mainClass = JarFile(jar).use { it.manifest?.mainAttributes?.getValue("Main-Class") }
            ?: error("В ${processor.jar} нет Main-Class")

        val args = processor.args.map { arg ->
            when {
                // [maven:coord] -> путь к артефакту
                arg.startsWith("[") && arg.endsWith("]") ->
                    File(librariesDir, Rules.mavenPath(arg.trim('[', ']'))).absolutePath
                else -> substitute(arg, tokens)
            }
        }

        val command = listOf(java.absolutePath, "-cp", classpath, mainClass) + args
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        check(code == 0) {
            "Процессор $mainClass завершился с кодом $code:\n${output.takeLast(2000)}"
        }

        // Проверяем, что заявленные результаты действительно получены.
        outputs.forEach { (path, sha1) ->
            val file = File(path)
            check(file.isFile) { "Процессор $mainClass не создал $path" }
            if (sha1.isNotBlank()) {
                val actual = Downloader.sha1(file)
                check(actual.equals(sha1.trim('\''), true)) {
                    "Результат $path не совпал по SHA-1 (ожидалось $sha1)"
                }
            }
        }
    }

    private fun substitute(value: String, tokens: Map<String, String>): String {
        var result = value
        tokens.forEach { (key, replacement) -> result = result.replace("{$key}", replacement) }
        return result
    }
}
