package dev.aether.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.aether.core.loader.Loader
import dev.aether.core.meta.VersionEntry
import dev.aether.ui.LauncherState

private val filters = listOf(
    "all" to "Все",
    "release" to "Release",
    "snapshot" to "Snapshot",
    "old_beta" to "Старые",
    "installed" to "Установленные",
)

@Composable
fun LibraryScreen(state: LauncherState) {
    val installed = remember(state.versions) { state.installedVersionIds() }

    val visible = state.versions.filter { entry ->
        val byFilter = when (state.filter) {
            "all" -> true
            "installed" -> entry.id in installed
            "old_beta" -> entry.type.startsWith("old")
            else -> entry.type == state.filter
        }
        byFilter && (state.search.isBlank() || entry.id.contains(state.search, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Библиотека версий", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = state.search,
                onValueChange = { state.search = it },
                placeholder = { Text("Поиск версии") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.width(280.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { (id, label) ->
                FilterChip(
                    selected = state.filter == id,
                    onClick = { state.filter = id },
                    label = { Text(label) },
                    leadingIcon = if (state.filter == id) {
                        { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                    } else null,
                )
            }
        }

        LoaderPicker(state)

        if (state.versionsLoading && state.versions.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(280.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(visible, key = { it.id }) { entry ->
                    VersionCard(
                        entry = entry,
                        installed = entry.id in installed,
                        selected = entry.id == state.selectedVersionId,
                        onSelect = { state.selectGameVersion(entry.id) },
                        onPlay = { state.selectGameVersion(entry.id); state.play() },
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionCard(
    entry: VersionEntry,
    installed: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(entry.id, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    entry.releaseTime.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(entry.type) },
                    enabled = false,
                )
                Button(onClick = onPlay, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Icon(if (installed) Icons.Default.PlayArrow else Icons.Default.Download, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (installed) "Играть" else "Скачать", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (entry.complianceLevel == 0) {
                Text(
                    "Версия выпущена до появления современных мер безопасности игроков",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Выбор модлоадера для текущей версии игры.
 *
 * NeoForge показывается только для 1.20.2+. Версия загрузчика по умолчанию —
 * последняя доступная; выпадающий список нужен тем, кому важна конкретная
 * сборка (обычно ради совместимости с модпаком).
 */
@Composable
private fun LoaderPicker(state: LauncherState) {
    val loaders = state.availableLoaders()
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SingleChoiceSegmentedButtonRow {
            loaders.forEachIndexed { index, loader ->
                SegmentedButton(
                    selected = state.loader == loader,
                    onClick = { state.selectLoader(loader) },
                    shape = SegmentedButtonDefaults.itemShape(index, loaders.size),
                    label = { Text(loader.displayName) },
                )
            }
        }

        if (state.loader != Loader.VANILLA) {
            Box {
                OutlinedButton(onClick = { menuOpen = true }, enabled = state.loaderVersions.isNotEmpty()) {
                    Text(
                        when {
                            state.loaderVersionsLoading -> "Загрузка версий…"
                            state.loaderVersions.isEmpty() -> "Нет сборок"
                            else -> state.loaderVersion ?: "Версия загрузчика"
                        }
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    state.loaderVersions.take(40).forEach { candidate ->
                        DropdownMenuItem(
                            text = {
                                Text(candidate.version + if (candidate.recommended) "  ·  рекомендуется" else "")
                            },
                            onClick = {
                                state.loaderVersion = candidate.version
                                menuOpen = false
                            },
                        )
                    }
                }
            }
            if (state.loaderVersionsLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}
