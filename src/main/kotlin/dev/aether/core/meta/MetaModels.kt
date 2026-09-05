package dev.aether.core.meta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ---------- version_manifest_v2.json ----------

@Serializable
data class VersionManifest(val latest: LatestVersions, val versions: List<VersionEntry>)

@Serializable
data class LatestVersions(val release: String, val snapshot: String)

@Serializable
data class VersionEntry(
    val id: String,
    val type: String,               // release | snapshot | old_beta | old_alpha
    val url: String,
    val time: String,
    val releaseTime: String,
    val sha1: String? = null,       // только в v2
    val complianceLevel: Int = 0,   // 0 -> нет современных мер безопасности игроков
)

// ---------- <version>.json ----------

@Serializable
data class VersionJson(
    val id: String,
    val type: String = "release",
    val mainClass: String,
    val inheritsFrom: String? = null,
    val assets: String? = null,
    val assetIndex: AssetIndexRef? = null,
    val downloads: VersionDownloads? = null,
    val libraries: List<Library> = emptyList(),
    val arguments: Arguments? = null,
    val minecraftArguments: String? = null,  // формат до 1.13
    val javaVersion: JavaVersionRef? = null,
    val logging: Logging? = null,
    val releaseTime: String? = null,
    val complianceLevel: Int = 0,
)

@Serializable
data class VersionDownloads(val client: Artifact? = null, val server: Artifact? = null)

@Serializable
data class AssetIndexRef(
    val id: String,
    val sha1: String,
    val size: Long = 0,
    val totalSize: Long = 0,
    val url: String,
)

@Serializable
data class JavaVersionRef(val component: String = "jre-legacy", val majorVersion: Int = 8)

@Serializable
data class Library(
    val name: String,
    val downloads: LibraryDownloads? = null,
    val url: String? = null,                       // Maven-корень (Fabric/Forge)
    val rules: List<Rule>? = null,
    val natives: Map<String, String>? = null,      // os -> classifier, формат до 1.19
    val extract: ExtractRule? = null,
)

@Serializable
data class LibraryDownloads(
    val artifact: Artifact? = null,
    val classifiers: Map<String, Artifact>? = null,
)

@Serializable
data class Artifact(
    val path: String? = null,
    val sha1: String,
    val size: Long = 0,
    val url: String,
)

@Serializable
data class ExtractRule(val exclude: List<String> = emptyList())

@Serializable
data class Rule(
    val action: String,                            // allow | disallow
    val os: OsRule? = null,
    val features: Map<String, Boolean>? = null,
)

@Serializable
data class OsRule(val name: String? = null, val version: String? = null, val arch: String? = null)

@Serializable
data class Arguments(
    val game: List<JsonElement> = emptyList(),
    val jvm: List<JsonElement> = emptyList(),
)

@Serializable
data class Logging(val client: LoggingClient? = null)

@Serializable
data class LoggingClient(val argument: String, val file: LoggingFile, val type: String)

@Serializable
data class LoggingFile(val id: String, val sha1: String, val size: Long = 0, val url: String)

// ---------- asset index ----------

@Serializable
data class AssetIndex(
    val objects: Map<String, AssetObject> = emptyMap(),
    val virtual: Boolean = false,
    @SerialName("map_to_resources") val mapToResources: Boolean = false,
)

@Serializable
data class AssetObject(val hash: String, val size: Long = 0) {
    val prefix: String get() = hash.substring(0, 2)
    val path: String get() = "$prefix/$hash"
}

// ---------- java-runtime/all.json ----------

@Serializable
data class JavaRuntimeEntry(val manifest: Artifact, val version: JavaRuntimeVersion)

@Serializable
data class JavaRuntimeVersion(val name: String, val released: String? = null)

@Serializable
data class JavaRuntimeManifest(val files: Map<String, JavaRuntimeFile> = emptyMap())

@Serializable
data class JavaRuntimeFile(
    val type: String,                              // file | directory | link
    val downloads: Map<String, Artifact>? = null,  // raw | lzma
    val executable: Boolean = false,
    val target: String? = null,
)
