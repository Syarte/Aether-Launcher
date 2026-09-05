package dev.aether.core.net

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Единственный HTTP-клиент приложения.
 *
 * Политика прозрачности: лаунчер ходит только на домены из [ALLOWED_HOSTS].
 * Никакой телеметрии, аналитики и сторонних SDK — проверяется тестом
 * `NetworkPolicyTest`, который падает при появлении лишнего хоста.
 */
object Http {

    val ALLOWED_HOSTS = setOf(
        // Microsoft Identity Platform
        "login.microsoftonline.com",
        // Xbox Live
        "user.auth.xboxlive.com",
        "xsts.auth.xboxlive.com",
        // Minecraft Services
        "api.minecraftservices.com",
        // Метаданные и контент игры
        "piston-meta.mojang.com",
        "piston-data.mojang.com",
        "launchermeta.mojang.com",
        "libraries.minecraft.net",
        "resources.download.minecraft.net",
        // Модлоадеры (запросы уходят только если пользователь выбрал загрузчик)
        "meta.fabricmc.net",
        "maven.fabricmc.net",
        "maven.minecraftforge.net",
        "maven.neoforged.net",
        // Часть библиотек Forge лежит в центральном Maven
        "repo1.maven.org",
    )

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    const val USER_AGENT = "Aether/0.1.0 (+https://github.com/aether-launcher)"

    val client: HttpClient = HttpClient(CIO) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 60_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay()
        }
        install(UserAgent) { agent = USER_AGENT }
        engine {
            // Явно фиксируем доверие только системному truststore: никаких кастомных CA.
            maxConnectionsCount = 64
            endpoint { maxConnectionsPerRoute = 16 }
        }
    }

    fun assertAllowed(url: String) {
        val host = java.net.URI(url).host ?: error("Некорректный URL: $url")
        require(host in ALLOWED_HOSTS || host == "127.0.0.1") {
            "Запрещённый хост: $host. Разрешены только официальные эндпоинты."
        }
    }
}
