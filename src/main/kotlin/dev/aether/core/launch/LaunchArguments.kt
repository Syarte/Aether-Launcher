package dev.aether.core.launch

import dev.aether.core.Platform
import dev.aether.core.install.InstalledGame
import dev.aether.core.install.Rules
import dev.aether.core.meta.Arguments
import kotlinx.serialization.json.*
import java.io.File

/**
 * Сборка командной строки по дескриптору версии.
 *
 * Поддерживаются оба формата:
 *  - современный (1.13+): `arguments.jvm` / `arguments.game` с правилами;
 *  - legacy: строка `minecraftArguments` + фиксированные JVM-аргументы.
 *
 * Плейсхолдеры подставляются ровно те, что определены спецификацией
 * официального лаунчера. Никаких дополнительных инъекций в процесс игры нет:
 * javaagent, -D для обхода проверок и подмена classpath не используются.
 */
object LaunchArguments {

    data class Context(
        val game: InstalledGame,
        val gameDir: File,
        val playerName: String,
        val playerUuid: String,
        val accessToken: String,
        val xuid: String?,
        val clientId: String,
        val ramMb: Int,
        val extraJvmArgs: List<String> = emptyList(),
        val extraGameArgs: List<String> = emptyList(),
        val launcherName: String = "aether",
        val launcherVersion: String = "0.1.0",
        val features: Map<String, Boolean> = emptyMap(),
    )

    fun build(ctx: Context): List<String> {
        val version = ctx.game.version
        val classpath = (ctx.game.classpath + ctx.game.clientJar)
            .distinct().filter { it.isFile }
            .joinToString(Platform.classpathSeparator) { it.absolutePath }

        val vars = mapOf(
            "auth_player_name" to ctx.playerName,
            "version_name" to version.id,
            "game_directory" to ctx.gameDir.absolutePath,
            "assets_root" to ctx.game.assetsDir.absolutePath,
            "game_assets" to (ctx.game.virtualAssetsDir?.absolutePath ?: ctx.game.assetsDir.absolutePath),
            "assets_index_name" to (version.assetIndex?.id ?: version.assets ?: "legacy"),
            "auth_uuid" to ctx.playerUuid,
            "auth_access_token" to ctx.accessToken,
            "auth_session" to "token:${ctx.accessToken}:${ctx.playerUuid}",
            "auth_xuid" to (ctx.xuid ?: ""),
            "clientid" to ctx.clientId,
            "user_type" to "msa",
            "user_properties" to "{}",
            "version_type" to version.type,
            "natives_directory" to ctx.game.nativesDir.absolutePath,
            "launcher_name" to ctx.launcherName,
            "launcher_version" to ctx.launcherVersion,
            "classpath" to classpath,
            "classpath_separator" to Platform.classpathSeparator,
            "library_directory" to File(ctx.gameDir, "libraries").absolutePath,
            "resolution_width" to "854",
            "resolution_height" to "480",
        )

        val command = mutableListOf<String>()

        // ---- JVM ----
        command += memoryArgs(ctx.ramMb)
        command += defaultJvmArgs(ctx)

        val jvmFromJson = version.arguments?.jvm.orEmpty()
        if (jvmFromJson.isNotEmpty()) {
            command += expand(jvmFromJson, vars, ctx.features)
        } else {
            // Формат до 1.13 не описывает JVM-аргументы.
            command += "-Djava.library.path=${ctx.game.nativesDir.absolutePath}"
            command += "-cp"
            command += classpath
        }
        ctx.game.loggingConfig?.let { config ->
            version.logging?.client?.let { logging ->
                command += logging.argument.replace("\${path}", config.absolutePath)
            }
        }
        command += ctx.extraJvmArgs

        // ---- Главный класс ----
        command += version.mainClass

        // ---- Аргументы игры ----
        val gameFromJson = version.arguments?.game.orEmpty()
        command += if (gameFromJson.isNotEmpty()) {
            expand(gameFromJson, vars, ctx.features)
        } else {
            version.minecraftArguments.orEmpty()
                .split(" ").filter { it.isNotBlank() }
                .map { substitute(it, vars) }
        }
        command += ctx.extraGameArgs

        return command
    }

    private fun memoryArgs(ramMb: Int) = listOf("-Xms${(ramMb / 2).coerceAtLeast(512)}M", "-Xmx${ramMb}M")

    private fun defaultJvmArgs(ctx: Context): List<String> = buildList {
        // Профиль GC, который использует официальный лаунчер для современных версий.
        add("-XX:+UnlockExperimentalVMOptions")
        add("-XX:+UseG1GC")
        add("-XX:G1NewSizePercent=20")
        add("-XX:G1ReservePercent=20")
        add("-XX:MaxGCPauseMillis=50")
        add("-XX:G1HeapRegionSize=32M")
        add("-Dfile.encoding=UTF-8")
        if (Platform.isMac) add("-XstartOnFirstThread")
        if (Platform.isWindows) add("-Dos.name=Windows 10")
    }

    /** Разворачивает смешанный список строк и условных блоков `{rules, value}`. */
    private fun expand(
        items: List<JsonElement>,
        vars: Map<String, String>,
        features: Map<String, Boolean>,
    ): List<String> = buildList {
        for (item in items) {
            when (item) {
                is JsonPrimitive -> add(substitute(item.content, vars))
                is JsonObject -> {
                    val rules = item["rules"]?.let {
                        dev.aether.core.net.Http.json.decodeFromJsonElement(
                            kotlinx.serialization.builtins.ListSerializer(dev.aether.core.meta.Rule.serializer()), it
                        )
                    }
                    if (!Rules.allows(rules, features)) continue
                    when (val value = item["value"]) {
                        is JsonPrimitive -> add(substitute(value.content, vars))
                        is JsonArray -> value.forEach { add(substitute(it.jsonPrimitive.content, vars)) }
                        else -> Unit
                    }
                }
                else -> Unit
            }
        }
    }

    private fun substitute(template: String, vars: Map<String, String>): String {
        var result = template
        for ((key, value) in vars) result = result.replace("\${$key}", value)
        return result
    }

    /** Маскирует токены в строке команды перед выводом в лог. */
    fun redact(command: List<String>, vararg secrets: String): List<String> =
        command.map { arg -> secrets.fold(arg) { acc, s -> if (s.isNotBlank()) acc.replace(s, "***") else acc } }
}
