package dev.aether.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.aether.ui.Avatar
import dev.aether.ui.LauncherState
import dev.aether.ui.Screen

@Composable
fun HomeScreen(state: LauncherState) {
    val account = state.activeAccount
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column {
            Text(
                if (account != null) "Привет, ${account.username}" else "Добро пожаловать",
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                if (account != null) "Готовы к игре?" else "Войдите через Microsoft, чтобы начать",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Avatar(account?.username, 72.dp)

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(account?.username ?: "Гость", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (account != null) "Microsoft · лицензия подтверждена" else "Аккаунт не подключён",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        onClick = { state.screen = Screen.LIBRARY },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Default.VideogameAsset, null, Modifier.size(18.dp))
                            Text(
                                buildString {
                                    append(state.selectedVersionId ?: "Выбрать версию")
                                    if (state.loader != dev.aether.core.loader.Loader.VANILLA) {
                                        append(" · ").append(state.loader.displayName)
                                        state.loaderVersion?.let { append(' ').append(it) }
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Icon(Icons.Default.ExpandMore, null, Modifier.size(16.dp))
                        }
                    }
                }

                if (account == null) {
                    Button(onClick = { state.screen = Screen.PROFILES }) { Text("Войти") }
                } else {
                    Button(
                        onClick = { state.play() },
                        enabled = !state.launching && state.session == null,
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(10.dp))
                        Text(if (state.session != null) "Игра запущена" else "Играть")
                    }
                }
            }
        }

        state.error?.let { message ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                Text(
                    message,
                    Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (state.logLines.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Журнал игры", style = MaterialTheme.typography.titleMedium)
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        state.logLines.takeLast(80).forEach {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
