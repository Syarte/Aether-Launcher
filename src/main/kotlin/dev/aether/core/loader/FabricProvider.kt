package dev.aether.core.loader

import dev.aether.core.install.GamePaths
import dev.aether.core.net.Http
import dev.aether.core.net.Progress
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class FabricLoaderEntry(val loader: FabricLoaderInfo, val intermediary: FabricIntermediary? = null)

@Serializable
private data class FabricLoaderInfo(val version: String, val build: Int = 0, val stable: Boolean = false)

@Serializable
private data class FabricIntermediary(val version: String, val stable: Boolean = true)

/**
 * Fabric. Самый простой случай: meta-API сразу отдаёт готовый профиль запуска
 * с `inheritsFrom`, никаких процессоров и патчинга клиента не требуется.
 *
 *   GET https://meta.fabricmc.net/v2/versions/loader/<mc>
 *   GET https://meta.fabricmc.net/v2/versions/loader/<mc>/<loader>/profile/json
 *
 * Библиотеки Fabric описаны Maven-корнем `https://maven.fabricmc.net/`,
 * который уже поддержан в GameInstaller.
 */
class FabricProvider : LoaderProvider {

    override val loader = Loader.FABRIC

    private val meta = "https://meta.fabricmc.net/v2"

    override suspend fun availableVersions(gameVersion: String): List<LoaderVersion> {
        val url = "$meta/versions/loader/$gameVersion"
        Http.assertAllowed(url)
        val response = Http.client.get(url)
        if (response.status.value !in 200..299) return emptyList()
        val entries: List<FabricLoaderEntry> = Http.json.decodeFromString(response.bodyAsText())
        return entries.mapIndexed { index, entry ->
            LoaderVersion(
                loader = Loader.FABRIC,
                version = entry.loader.version,
                recommended = index == 0 && entry.loader.stable,
                stable = entry.loader.stable,
            )
        }
    }

    override suspend fun install(
        gameVersion: String,
        loaderVersion: String,
        paths: GamePaths,
        java: File,
        onProgress: (Progress) -> Unit,
    ): String {
        onProgress(Progress("Установка Fabric", 0, 1))
        val url = "$meta/versions/loader/$gameVersion/$loaderVersion/profile/json"
        Http.assertAllowed(url)
        val response = Http.client.get(url)
        check(response.status.value in 200..299) {
            "Fabric не отдал профиль для $gameVersion / $loaderVersion (HTTP ${response.status.value})"
        }
        val profileJson = response.bodyAsText()
        val id = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(profileJson)?.groupValues?.get(1)
            ?: "fabric-loader-$loaderVersion-$gameVersion"

        val target = File(paths.versionDir(id), "$id.json")
        target.parentFile.mkdirs()
        target.writeText(profileJson)
        onProgress(Progress("Установка Fabric", 1, 1))
        return id
    }
}
