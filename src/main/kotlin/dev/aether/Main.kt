package dev.aether

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.aether.ui.App
import dev.aether.ui.LauncherState

/**
 * Client ID приложения из Microsoft Entra ID.
 *
 * Задаётся при сборке (`-Daether.clientId=...`) или переменной окружения,
 * чтобы идентификатор не был вшит в исходники. Приложение регистрируется
 * как public client; client secret не используется и не нужен.
 */
private fun clientId(): String =
    System.getProperty("aether.clientId")
        ?: System.getenv("AETHER_CLIENT_ID")
        ?: error(
            "Не задан client ID. Укажите -Daether.clientId=<GUID> или переменную AETHER_CLIENT_ID. " +
                "Порядок регистрации приложения описан в README."
        )

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1440.dp, 900.dp))
    val scope = rememberCoroutineScope()
    val state = remember { LauncherState(scope, clientId()) }

    LaunchedEffect(Unit) { state.bootstrap() }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Aether",
    ) {
        App(state, systemDark = true)
    }
}
