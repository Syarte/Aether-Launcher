package dev.aether.core.auth

import dev.aether.core.net.Http
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Шаг 1 общей цепочки: Microsoft Identity Platform.
 *
 * Эндпоинты (tenant `consumers` — Minecraft живёт только на личных аккаунтах MSA):
 *   POST https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode
 *   GET  https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize
 *   POST https://login.microsoftonline.com/consumers/oauth2/v2.0/token
 *
 * Scope: `XboxLive.signin offline_access`. Client secret не используется —
 * приложение регистрируется как public client, секрет в десктопном
 * дистрибутиве всё равно не является секретом (RFC 8252).
 */
class MicrosoftAuth(private val clientId: String) {

    private val base = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
    private val scope = "XboxLive.signin offline_access"

    data class DeviceCodePrompt(val userCode: String, val verificationUri: String, val expiresIn: Int)

    // ------------------------------------------------------------------
    // Вариант A: Device Code Flow.
    // Работает без локального сервера и без встроенного браузера,
    // поэтому переживает корпоративные прокси и не требует свободного порта.
    // ------------------------------------------------------------------

    suspend fun startDeviceCode(): DeviceCodeResponse {
        val response = Http.client.submitForm(
            url = "$base/devicecode",
            formParameters = parameters {
                append("client_id", clientId)
                append("scope", scope)
            }
        )
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw AuthException("Microsoft отклонил запрос device code: $body")
        }
        return Http.json.decodeFromString(DeviceCodeResponse.serializer(), body)
    }

    /** Опрашивает token endpoint, пока пользователь не подтвердит вход в браузере. */
    suspend fun pollDeviceCode(dc: DeviceCodeResponse): MsTokenResponse {
        var interval = dc.interval.toLong()
        val deadline = System.currentTimeMillis() + dc.expiresIn * 1000L

        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000)
            val token = tokenRequest {
                append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                append("client_id", clientId)
                append("device_code", dc.deviceCode)
            }
            when (token.error) {
                null -> return token
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5
                "authorization_declined" -> throw AuthException("Вход отменён пользователем")
                "expired_token" -> throw AuthException("Код входа истёк, начните заново")
                else -> throw AuthException("Microsoft: ${token.error} — ${token.errorDescription}")
            }
        }
        throw AuthException("Код входа истёк, начните заново")
    }

    // ------------------------------------------------------------------
    // Вариант B: Authorization Code + PKCE на loopback-редиректе (RFC 8252).
    // Даёт более короткий путь в один клик; используется по умолчанию,
    // с автоматическим откатом на device code при недоступности порта.
    // ------------------------------------------------------------------

    suspend fun authorizationCodeFlow(openBrowser: (String) -> Unit): MsTokenResponse {
        val verifier = randomUrlSafe(64)
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = randomUrlSafe(24)
        val port = freePort()
        val redirectUri = "http://localhost:$port/callback"

        val received = CompletableDeferred<String>()
        val server = embeddedServer(ServerCIO, port = port, host = "127.0.0.1") {
            routing {
                get("/callback") {
                    val code = call.request.queryParameters["code"]
                    val returnedState = call.request.queryParameters["state"]
                    if (code != null && returnedState == state) {
                        call.respondText(SUCCESS_PAGE, ContentType.Text.Html)
                        received.complete(code)
                    } else {
                        val err = call.request.queryParameters["error_description"] ?: "неизвестная ошибка"
                        call.respondText("Вход не завершён: $err", ContentType.Text.Plain)
                        received.completeExceptionally(AuthException("Microsoft вернул ошибку: $err"))
                    }
                }
            }
        }.start(wait = false)

        try {
            val url = URLBuilder("$base/authorize").apply {
                parameters.append("client_id", clientId)
                parameters.append("response_type", "code")
                parameters.append("redirect_uri", redirectUri)
                parameters.append("response_mode", "query")
                parameters.append("scope", scope)
                parameters.append("state", state)
                parameters.append("code_challenge", challenge)
                parameters.append("code_challenge_method", "S256")
                parameters.append("prompt", "select_account")
            }.buildString()
            openBrowser(url)

            val code = withTimeoutOrNull(5 * 60_000) { received.await() }
                ?: throw AuthException("Истекло время ожидания входа (5 минут)")

            return tokenRequest {
                append("grant_type", "authorization_code")
                append("client_id", clientId)
                append("code", code)
                append("redirect_uri", redirectUri)
                append("code_verifier", verifier)
            }.orThrow()
        } finally {
            server.stop(0, 1000)
        }
    }

    /** Тихое обновление сессии по refresh-токену — при каждом старте лаунчера. */
    suspend fun refresh(refreshToken: String): MsTokenResponse = tokenRequest {
        append("grant_type", "refresh_token")
        append("client_id", clientId)
        append("refresh_token", refreshToken)
        append("scope", scope)
    }.orThrow()

    // ------------------------------------------------------------------

    private suspend fun tokenRequest(build: ParametersBuilder.() -> Unit): MsTokenResponse {
        val response = Http.client.submitForm(url = "$base/token", formParameters = parameters(build))
        return Http.json.decodeFromString(MsTokenResponse.serializer(), response.bodyAsText())
    }

    private fun MsTokenResponse.orThrow(): MsTokenResponse {
        if (error != null) {
            val hint = when (error) {
                "invalid_client", "unauthorized_client" ->
                    "Проверьте, что в Entra ID включён режим public client (Allow public client flows)."
                else -> null
            }
            throw AuthException("Microsoft: $error — $errorDescription", hint)
        }
        return this
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buf = ByteArray(bytes)
        SecureRandom().nextBytes(buf)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

    private companion object {
        const val SUCCESS_PAGE = """
            <!doctype html><meta charset="utf-8">
            <title>Вход выполнен</title>
            <body style="font-family:system-ui;display:grid;place-items:center;height:100vh;margin:0;background:#101410;color:#e6f0e6">
              <div style="text-align:center">
                <h2 style="font-weight:500">Вход выполнен</h2>
                <p style="opacity:.7">Можно закрыть вкладку и вернуться в Aether.</p>
              </div>
            </body>
        """
    }
}


