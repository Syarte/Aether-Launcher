package dev.aether.core.meta

import dev.aether.core.Platform
import dev.aether.core.net.Downloader
import dev.aether.core.net.Http
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.io.File

/**
 * Доступ к официальным метаданным Mojang (piston-meta).
 *
 * Все ответы кэшируются на диск; version JSON дополнительно сверяется
 * с SHA-1 из манифеста v2 — это защищает от подмены дескриптора запуска.
 */
class MetaClient(private val cacheDir: File = File(Platform.dataDir, "meta")) {

    companion object {
        const val VERSION_MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        const val JAVA_RUNTIMES =
            "https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"
        const val RESOURCES = "https://resources.download.minecraft.net"
        const val LIBRARIES = "https://libraries.minecraft.net"
    }

    init { cacheDir.mkdirs() }

    suspend fun versionManifest(): VersionManifest {
        val text = fetchText(VERSION_MANIFEST)
        File(cacheDir, "version_manifest_v2.json").writeText(text)
        return Http.json.decodeFromString(VersionManifest.serializer(), text)
    }

    /** Читает кэш, если сеть недоступна: библиотека версий работает офлайн. */
    fun cachedVersionManifest(): VersionManifest? {
        val f = File(cacheDir, "version_manifest_v2.json")
        if (!f.isFile) return null
        return runCatching { Http.json.decodeFromString(VersionManifest.serializer(), f.readText()) }.getOrNull()
    }

    suspend fun versionJson(entry: VersionEntry, versionsDir: File): VersionJson {
        val target = File(versionsDir, "${entry.id}/${entry.id}.json")
        val task = dev.aether.core.net.DownloadTask(entry.url, target, entry.sha1)
        if (!Downloader.isValid(task)) Downloader.download(task)
        return Http.json.decodeFromString(VersionJson.serializer(), target.readText())
    }

    /**
     * Собирает эффективный дескриптор с учётом inheritsFrom (Fabric/Forge
     * публикуют «дельту» поверх ванильной версии).
     */
    suspend fun resolveVersion(id: String, versionsDir: File): VersionJson {
        val local = File(versionsDir, "$id/$id.json")
        val json = if (local.isFile) {
            Http.json.decodeFromString(VersionJson.serializer(), local.readText())
        } else {
            val manifest = versionManifest()
            val entry = manifest.versions.firstOrNull { it.id == id }
                ?: error("Версия $id отсутствует в манифесте Mojang")
            versionJson(entry, versionsDir)
        }
        val parentId = json.inheritsFrom ?: return json
        val parent = resolveVersion(parentId, versionsDir)
        return merge(parent, json)
    }

    private fun merge(parent: VersionJson, child: VersionJson): VersionJson = child.copy(
        // Библиотеки ребёнка идут первыми: их версии перекрывают родительские в classpath.
        libraries = child.libraries + parent.libraries,
        assetIndex = child.assetIndex ?: parent.assetIndex,
        assets = child.assets ?: parent.assets,
        downloads = child.downloads ?: parent.downloads,
        javaVersion = child.javaVersion ?: parent.javaVersion,
        logging = child.logging ?: parent.logging,
        minecraftArguments = child.minecraftArguments ?: parent.minecraftArguments,
        arguments = when {
            child.arguments == null -> parent.arguments
            parent.arguments == null -> child.arguments
            else -> Arguments(
                game = parent.arguments.game + child.arguments.game,
                jvm = parent.arguments.jvm + child.arguments.jvm,
            )
        },
    )

    suspend fun assetIndex(ref: AssetIndexRef, assetsDir: File): AssetIndex {
        val target = File(assetsDir, "indexes/${ref.id}.json")
        val task = dev.aether.core.net.DownloadTask(ref.url, target, ref.sha1, ref.size)
        if (!Downloader.isValid(task)) Downloader.download(task)
        return Http.json.decodeFromString(AssetIndex.serializer(), target.readText())
    }

    suspend fun javaRuntimes(): Map<String, Map<String, List<JavaRuntimeEntry>>> {
        val text = fetchText(JAVA_RUNTIMES)
        return Http.json.decodeFromString(text)
    }

    suspend fun javaRuntimeManifest(url: String): JavaRuntimeManifest =
        Http.json.decodeFromString(JavaRuntimeManifest.serializer(), fetchText(url))

    private suspend fun fetchText(url: String): String {
        Http.assertAllowed(url)
        val response = Http.client.get(url)
        check(response.status.value in 200..299) { "$url -> HTTP ${response.status.value}" }
        return response.bodyAsText()
    }
}
