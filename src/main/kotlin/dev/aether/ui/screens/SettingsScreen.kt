package dev.aether.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.aether.ui.LauncherState
import dev.aether.ui.SeedPalette
import dev.aether.ui.theme.ThemeMode

@Composable
fun SettingsScreen(state: LauncherState, seed: Color, onSeed: (Color) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp).widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)

        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Выделение памяти (RAM)", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Больше памяти помогает модовым сборкам; для ванильной игры хватает 4 ГБ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("2 ГБ", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.ramMb.toFloat(),
                    onValueChange = { state.ramMb = (it / 512).toInt() * 512 },
                    valueRange = 2048f..state.maxRamMb.toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Text("${state.maxRamMb / 1024} ГБ", style = MaterialTheme.typography.labelMedium)
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
                    Text(
                        "${state.ramMb / 1024} ГБ",
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text("Путь установки", style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.paths.root.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { /* выбор каталога через JFileChooser */ }) { Text("Обзор") }
            }
        }

        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Default.Code, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text("Java", style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.javaOverride?.absolutePath ?: "Автоматически — рантайм Mojang под версию игры",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { state.javaOverride = null }) { Text("Сбросить") }
            }
        }

        SettingCard {
            Text("Тема", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.LIGHT to "Светлая",
                    ThemeMode.DARK to "Тёмная",
                    ThemeMode.SYSTEM to "Системная",
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { state.themeMode = mode },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Акцентный цвет", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Вся палитра пересчитывается из выбранного тона",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SeedPalette.forEach { (name, color) ->
                    Box(
                        Modifier.size(40.dp).background(color, CircleShape)
                            .then(
                                if (seed == color) Modifier.padding(4.dp) else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(onClick = { onSeed(color) }, content = {})
                    }
                }
            }
        }

        SettingCard {
            Text("Приватность", style = MaterialTheme.typography.titleMedium)
            Text(
                "Лаунчер обращается только к серверам Microsoft, Xbox Live и Mojang. " +
                    "Аналитики, трекеров и сторонних SDK в сборке нет; список разрешённых доменов " +
                    "фиксирован в коде и проверяется тестом.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
