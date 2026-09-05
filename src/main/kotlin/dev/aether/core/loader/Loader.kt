package dev.aether.core.loader

import dev.aether.core.install.GamePaths
import dev.aether.core.net.Progress
import java.io.File

enum class Loader(val displayName: String) {
    VANILLA("Ваниль"),
    FABRIC("Fabric"),
    FORGE("Forge"),
    NEOFORGE("NeoForge"),
}

/** Версия модлоадера, пригодная для конкретной версии игры. */
data class LoaderVersion(
    val loader: Loader,
    val version: String,
    val recommended: Boolean = false,
    val stable: Boolean = true,
)

/**
 * Провайдер модлоадера. Задача одна: подготовить в `versions/<id>/<id>.json`
 * профиль запуска и вернуть его id. Дальше работает общий конвейер —
 * `MetaClient.resolveVersion` сольёт профиль с ванильным родителем через
 * `inheritsFrom`, а `GameInstaller` скачает библиотеки.
 */
interface LoaderProvider {
    val loader: Loader

    /** Версии загрузчика, доступные для указанной версии игры. */
    suspend fun availableVersions(gameVersion: String): List<LoaderVersion>

    /** Готовит профиль и возвращает id версии для запуска. */
    suspend fun install(
        gameVersion: String,
        loaderVersion: String,
        paths: GamePaths,
        java: File,
        onProgress: (Progress) -> Unit,
    ): String
}
