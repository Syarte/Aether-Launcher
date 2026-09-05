package dev.aether

import dev.aether.core.net.Http
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Проверяет заявленное отсутствие трекеров: в исходниках не должно быть
 * ни одного http(s)-адреса, который не входит в список разрешённых доменов.
 * Тест падает, если кто-то добавит аналитику или сторонний SDK.
 */
class NetworkPolicyTest {

    private val urlPattern = Regex("""https?://([A-Za-z0-9._-]+)""")

    @Test
    fun `в коде нет обращений к посторонним доменам`() {
        val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
        val violations = mutableListOf<String>()

        for (file in sources) {
            urlPattern.findAll(file.readText()).forEach { match ->
                val host = match.groupValues[1]
                val allowed = host in Http.ALLOWED_HOSTS ||
                    host == "127.0.0.1" ||
                    host == "localhost" ||
                    host.endsWith("github.com") ||          // только в комментариях/User-Agent
                    host == "launchercontent.mojang.com"    // официальный фид новостей
                if (!allowed) violations += "${file.name}: $host"
            }
        }
        if (violations.isNotEmpty()) fail("Найдены посторонние домены:\n" + violations.joinToString("\n"))
        assertTrue(Http.ALLOWED_HOSTS.isNotEmpty())
    }
}
