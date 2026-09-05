package dev.aether.core.auth

import dev.aether.core.net.Http
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Полная цепочка авторизации, строго по официальной схеме:
 *
 *   MSA access_token
 *     -> POST https://user.auth.xboxlive.com/user/authenticate      (RelyingParty http://auth.xboxlive.com)
 *     -> POST https://xsts.auth.xboxlive.com/xsts/authorize         (RelyingParty rp://api.minecraftservices.com/)
 *     -> POST https://api.minecraftservices.com/authentication/login_with_xbox
 *     -> GET  https://api.minecraftservices.com/entitlements/mcstore   (проверка лицензии)
 *     -> GET  https://api.minecraftservices.com/minecraft/profile      (UUID + ник)
 *
 * Ни один шаг не обходится и не эмулируется: без лицензии на Java Edition
 * профиль не выдаётся, и лаунчер не запускает игру.
 */
class MinecraftAuthenticator(private val microsoft: MicrosoftAuth) {

    private val xblUrl = "https://user.auth.xboxlive.com/user/authenticate"
    private val xstsUrl = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private val mcLoginUrl = "https://api.minecraftservices.com/authentication/login_with_xbox"
    private val entitlementsUrl = "https://api.minecraftservices.com/entitlements/mcstore"
    private val profileUrl = "https://api.minecraftservices.com/minecraft/profile"

    suspend fun completeLogin(ms: MsTokenResponse): Account {
        val msAccess = ms.accessToken ?: throw AuthException("Microsoft не вернул access_token")
        val refresh = ms.refreshToken ?: throw AuthException("Не выдан refresh_token: проверьте scope offline_access")

        val xbl = authenticateXboxLive(msAccess)
        val xsts = authorizeXsts(xbl.token)
        val mc = loginWithXbox(xsts.userHash, xsts.token)
        requireEntitlement(mc.accessToken)
        val profile = fetchProfile(mc.accessToken)

        return Account(
            uuid = profile.id,
            username = profile.name,
            xuid = xsts.xuid ?: xbl.xuid,
            accessToken = mc.accessToken,
            accessTokenExpiresAt = System.currentTimeMillis() + mc.expiresIn * 1000,
            refreshToken = refresh,
            skinUrl = profile.skins.firstOrNull { it.state == "ACTIVE" }?.url,
        )
    }

    /** Тихий рефреш при старте: MSA refresh -> заново вся цепочка Xbox. */
    suspend fun refresh(account: Account): Account {
        val ms = microsoft.refresh(account.refreshToken)
        return completeLogin(ms)
    }

    // ---- Шаг 2: Xbox Live user token ----
    private suspend fun authenticateXboxLive(msAccessToken: String): XboxResponse {
        val body = XboxRequest(
            properties = mapOf(
                "AuthMethod" to "RPS",
                "SiteName" to "user.auth.xboxlive.com",
                // Префикс d= обязателен для токенов, полученных через login.microsoftonline.com
                "RpsTicket" to "d=$msAccessToken",
            ),
            relyingParty = "http://auth.xboxlive.com",
        )
        val response = Http.client.post(xblUrl) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(Http.json.encodeToString(XboxRequest.serializer(), body))
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw AuthException("Xbox Live отклонил вход (HTTP ${response.status.value}): $text")
        }
        return Http.json.decodeFromString(XboxResponse.serializer(), text)
    }

    // ---- Шаг 3: XSTS token для Minecraft Services ----
    private suspend fun authorizeXsts(xblToken: String): XboxResponse {
        val body = XstsRequest(
            properties = XstsProperties(userTokens = listOf(xblToken)),
            relyingParty = "rp://api.minecraftservices.com/",
        )
        val response = Http.client.post(xstsUrl) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(Http.json.encodeToString(XstsRequest.serializer(), body))
        }
        val text = response.bodyAsText()

        if (response.status == HttpStatusCode.Unauthorized) {
            val err = runCatching { Http.json.decodeFromString(XstsError.serializer(), text) }.getOrNull()
            throw AuthException(xstsErrorMessage(err?.xErr ?: 0), hintFor(err?.xErr ?: 0))
        }
        if (response.status.value !in 200..299) {
            throw AuthException("XSTS отклонил запрос (HTTP ${response.status.value}): $text")
        }
        return Http.json.decodeFromString(XboxResponse.serializer(), text)
    }

    private fun xstsErrorMessage(xErr: Long): String = when (xErr) {
        2148916233L -> "У этого аккаунта Microsoft нет профиля Xbox"
        2148916235L -> "Xbox Live недоступен в регионе аккаунта"
        2148916236L, 2148916237L -> "Требуется подтверждение возраста в аккаунте Microsoft"
        2148916238L -> "Детский аккаунт: нужно добавить его в семейную группу"
        2148916227L -> "Аккаунт заблокирован за нарушение правил Xbox Live"
        else -> "XSTS отклонил запрос (XErr=$xErr)"
    }

    private fun hintFor(xErr: Long): String? = when (xErr) {
        2148916233L -> "Создайте профиль Xbox на xbox.com и повторите вход."
        2148916238L -> "Взрослый в семье должен разрешить доступ на account.microsoft.com/family."
        else -> null
    }

    // ---- Шаг 4: токен Minecraft Services ----
    private suspend fun loginWithXbox(userHash: String, xstsToken: String): MinecraftLoginResponse {
        val body = MinecraftLoginRequest(identityToken = "XBL3.0 x=$userHash;$xstsToken")
        val response = Http.client.post(mcLoginUrl) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(Http.json.encodeToString(MinecraftLoginRequest.serializer(), body))
        }
        val text = response.bodyAsText()
        if (response.status == HttpStatusCode.Forbidden && text.contains("app registration", ignoreCase = true)) {
            throw AuthException(
                "Minecraft Services отклонил client ID: приложение не одобрено для Minecraft API",
                "Client ID из Entra ID нужно отдельно согласовать с Microsoft/Mojang — см. README, раздел «Регистрация приложения»."
            )
        }
        if (response.status.value !in 200..299) {
            throw AuthException("Minecraft Services (HTTP ${response.status.value}): $text")
        }
        return Http.json.decodeFromString(MinecraftLoginResponse.serializer(), text)
    }

    // ---- Шаг 5: проверка лицензии ----
    private suspend fun requireEntitlement(mcToken: String) {
        val response = Http.client.get(entitlementsUrl) {
            header(HttpHeaders.Authorization, "Bearer $mcToken")
            accept(ContentType.Application.Json)
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw AuthException("Не удалось проверить лицензию (HTTP ${response.status.value})")
        }
        val entitlements = Http.json.decodeFromString(Entitlements.serializer(), text)
        val owns = entitlements.items.any { it.name == "product_minecraft" || it.name == "game_minecraft" }
        if (!owns) {
            throw AuthException(
                "На аккаунте нет лицензии Minecraft: Java Edition",
                "Купить можно на minecraft.net. Game Pass также выдаёт entitlement — войдите в игру хотя бы раз."
            )
        }
    }

    // ---- Шаг 6: профиль ----
    private suspend fun fetchProfile(mcToken: String): MinecraftProfile {
        val response = Http.client.get(profileUrl) {
            header(HttpHeaders.Authorization, "Bearer $mcToken")
            accept(ContentType.Application.Json)
        }
        val text = response.bodyAsText()
        if (response.status == HttpStatusCode.NotFound) {
            throw AuthException("Профиль Minecraft не создан", "Зайдите один раз в официальный лаунчер и выберите ник.")
        }
        if (response.status.value !in 200..299) {
            throw AuthException("Не удалось получить профиль (HTTP ${response.status.value}): $text")
        }
        return Http.json.decodeFromString(MinecraftProfile.serializer(), text)
    }
}
