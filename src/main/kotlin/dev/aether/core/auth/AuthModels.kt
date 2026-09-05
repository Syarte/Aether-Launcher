package dev.aether.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Microsoft Identity Platform ----------

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int,
    val interval: Int = 5,
    val message: String? = null,
)

@Serializable
data class MsTokenResponse(
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 0,
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

// ---------- Xbox Live / XSTS ----------

@Serializable
data class XboxRequest(
    @SerialName("Properties") val properties: Map<String, String>,
    @SerialName("RelyingParty") val relyingParty: String,
    @SerialName("TokenType") val tokenType: String = "JWT",
)

@Serializable
data class XstsRequest(
    @SerialName("Properties") val properties: XstsProperties,
    @SerialName("RelyingParty") val relyingParty: String,
    @SerialName("TokenType") val tokenType: String = "JWT",
)

@Serializable
data class XstsProperties(
    @SerialName("SandboxId") val sandboxId: String = "RETAIL",
    @SerialName("UserTokens") val userTokens: List<String>,
)

@Serializable
data class XboxResponse(
    @SerialName("IssueInstant") val issueInstant: String? = null,
    @SerialName("NotAfter") val notAfter: String? = null,
    @SerialName("Token") val token: String,
    @SerialName("DisplayClaims") val displayClaims: DisplayClaims,
) {
    val userHash: String get() = displayClaims.xui.first().uhs
    val xuid: String? get() = displayClaims.xui.firstOrNull()?.xid
}

@Serializable
data class DisplayClaims(val xui: List<Xui>)

@Serializable
data class Xui(val uhs: String, val xid: String? = null)

@Serializable
data class XstsError(
    @SerialName("Identity") val identity: String? = null,
    @SerialName("XErr") val xErr: Long = 0,
    @SerialName("Message") val message: String? = null,
    @SerialName("Redirect") val redirect: String? = null,
)

// ---------- Minecraft Services ----------

@Serializable
data class MinecraftLoginRequest(val identityToken: String)

@Serializable
data class MinecraftLoginResponse(
    val username: String,
    val roles: List<String> = emptyList(),
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long = 86_400,
)

@Serializable
data class Entitlements(val items: List<EntitlementItem> = emptyList())

@Serializable
data class EntitlementItem(val name: String, val signature: String? = null)

@Serializable
data class MinecraftProfile(
    val id: String,          // UUID без дефисов
    val name: String,
    val skins: List<ProfileSkin> = emptyList(),
    val capes: List<ProfileCape> = emptyList(),
)

@Serializable
data class ProfileSkin(val id: String, val state: String, val url: String, val variant: String? = null)

@Serializable
data class ProfileCape(val id: String, val state: String, val url: String, val alias: String? = null)

// ---------- Внутренняя модель аккаунта ----------

@Serializable
data class Account(
    val uuid: String,
    val username: String,
    val xuid: String?,
    /** Токен Minecraft Services, живёт ~24 часа. */
    val accessToken: String,
    val accessTokenExpiresAt: Long,
    /** Refresh token Microsoft — единственное, что храним долго. */
    val refreshToken: String,
    val skinUrl: String? = null,
) {
    val uuidDashed: String
        get() = buildString {
            append(uuid, 0, 8); append('-')
            append(uuid, 8, 12); append('-')
            append(uuid, 12, 16); append('-')
            append(uuid, 16, 20); append('-')
            append(uuid, 20, 32)
        }

    val isExpired: Boolean get() = System.currentTimeMillis() > accessTokenExpiresAt - 60_000
}

class AuthException(message: String, val hint: String? = null) : Exception(message)
