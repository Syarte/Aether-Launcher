package dev.aether.core.launch

import dev.aether.core.auth.Account
import dev.aether.core.install.InstalledGame
import kotlinx.coroutines.*
import java.io.File

/** Один запущенный экземпляр игры. */
class GameSession(
    val versionId: String,
    val process: Process,
    private val scope: CoroutineScope,
) {
    val isAlive: Boolean get() = process.isAlive
    fun kill() = process.destroy()
    fun close() = scope.cancel()
}

class GameLauncher(private val clientId: String) {

    /**
     * Запускает игру. Токен передаётся только как аргумент дочернего процесса
     * и никуда не логируется — в консоль лаунчера уходит замаскированная команда.
     */
    fun launch(
        java: File,
        game: InstalledGame,
        gameDir: File,
        account: Account,
        ramMb: Int,
        extraJvmArgs: List<String> = emptyList(),
        onLine: (String) -> Unit = {},
        onExit: (Int) -> Unit = {},
    ): GameSession {
        val args = LaunchArguments.build(
            LaunchArguments.Context(
                game = game,
                gameDir = gameDir,
                playerName = account.username,
                playerUuid = account.uuid,
                accessToken = account.accessToken,
                xuid = account.xuid,
                clientId = clientId,
                ramMb = ramMb,
                extraJvmArgs = extraJvmArgs,
            )
        )
        val command = listOf(java.absolutePath) + args
        gameDir.mkdirs()

        onLine("> " + LaunchArguments.redact(command, account.accessToken, account.refreshToken).joinToString(" "))

        val process = ProcessBuilder(command)
            .directory(gameDir)
            .redirectErrorStream(true)
            .start()

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { onLine(it) }
            }
            onExit(process.waitFor())
        }
        return GameSession(game.version.id, process, scope)
    }
}
