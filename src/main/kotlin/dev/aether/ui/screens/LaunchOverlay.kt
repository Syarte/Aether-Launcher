package dev.aether.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.aether.ui.LauncherState

private val STAGES = listOf("Проверка файлов", "Загрузка библиотек", "Загрузка ассетов", "Установка Java", "Запуск JVM")

/** Модальный оверлей запуска: стадии из макета + реальный прогресс загрузки. */
@Composable
fun LaunchOverlay(state: LauncherState) {
    AnimatedVisibility(state.launching, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                modifier = Modifier.width(420.dp),
            ) {
                Column(
                    Modifier.padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val spin = rememberInfiniteTransition(label = "spin")
                    val angle = spin.animateFloat(
                        0f, 360f,
                        infiniteRepeatable(tween(1200, easing = LinearEasing)),
                        label = "angle",
                    ).value
                    Icon(
                        Icons.Default.Autorenew, null,
                        Modifier.rotate(angle),
                        tint = MaterialTheme.colorScheme.primary,
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Запуск ${state.selectedVersionId ?: ""}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.progress.stage.ifBlank { "Подготовка" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    LinearProgressIndicator(
                        progress = { state.progress.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val currentIndex = STAGES.indexOf(state.progress.stage).coerceAtLeast(0)
                        STAGES.forEachIndexed { index, label ->
                            val done = index < currentIndex
                            val current = index == currentIndex
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    when {
                                        done -> Icons.Default.CheckCircle
                                        current -> Icons.Default.Autorenew
                                        else -> Icons.Default.RadioButtonUnchecked
                                    },
                                    null,
                                    Modifier.size(18.dp),
                                    tint = if (done || current) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                )
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (done || current) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    OutlinedButton(onClick = { state.cancelLaunch() }) { Text("Отмена") }
                }
            }
        }
    }
}

