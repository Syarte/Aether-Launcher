package dev.aether.core.loader

import dev.aether.core.meta.Library
import kotlinx.serialization.Serializable

/**
 * `install_profile.json` из инсталлятора Forge/NeoForge (формат 1.13+).
 *
 * Установка современного Forge — это не просто добавление библиотек:
 * клиентский jar нужно пропатчить и деобфусцировать локально, потому что
 * распространять готовый пропатченный клиент нельзя по лицензии Mojang.
 * За это отвечает список `processors` — цепочка утилит, которые
 * инсталлятор запускает у пользователя на машине.
 */
@Serializable
data class InstallProfile(
    val spec: Int = 0,
    val profile: String = "",
    val version: String = "",
    val minecraft: String = "",
    val json: String = "",
    val path: String? = null,
    val libraries: List<Library> = emptyList(),
    val processors: List<Processor> = emptyList(),
    val data: Map<String, DataEntry> = emptyMap(),
)

@Serializable
data class Processor(
    val sides: List<String> = emptyList(),   // пусто = обе стороны
    val jar: String,                          // Maven-координата процессора
    val classpath: List<String> = emptyList(),
    val args: List<String> = emptyList(),
    val outputs: Map<String, String> = emptyMap(),
)

@Serializable
data class DataEntry(val client: String = "", val server: String = "")
