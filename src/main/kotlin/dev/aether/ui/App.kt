package dev.aether.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.aether.ui.screens.*
import dev.aether.ui.theme.AetherTheme
import dev.aether.ui.theme.ThemeMode

/** Seed-цвета из макета: зелёный по умолчанию + альтернативы. */
val SeedPalette = listOf(
    "Изумруд" to Color(0xFF4CAF50),
    "Сирень" to Color(0xFF6750A4),
    "Лазурь" to Color(0xFF00A9E0),
    "Закат" to Color(0xFFFF7043),
)

@Composable
fun App(state: LauncherState, systemDark: Boolean) {
    val dark = when (state.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    var seed by remember { mutableStateOf(SeedPalette.first().second) }

    AetherTheme(seed = seed, dark = dark) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopBar(state, dark)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.weight(1f)) {
                    NavRail(state)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Content(state, seed) { seed = it }
                        LaunchOverlay(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: LauncherState, dark: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(28.dp)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.SportsEsports, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
        }
        Text("Aether", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = {
            state.themeMode = if (dark) ThemeMode.LIGHT else ThemeMode.DARK
        }) {
            Icon(if (dark) Icons.Default.LightMode else Icons.Default.DarkMode, "Сменить тему")
        }
        Avatar(state.activeAccount?.username, 32.dp)
    }
}

@Composable
fun Avatar(name: String?, size: androidx.compose.ui.unit.Dp) {
    val letter = name?.firstOrNull()?.uppercase() ?: "?"
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun CircleShape() = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50)

private data class NavItem(val screen: Screen, val label: String, val icon: ImageVector)

@Composable
private fun NavRail(state: LauncherState) {
    val items = listOf(
        NavItem(Screen.HOME, "Главная", Icons.Default.Home),
        NavItem(Screen.LIBRARY, "Библиотека", Icons.Default.GridView),
        NavItem(Screen.NEWS, "Новости", Icons.Default.Campaign),
        NavItem(Screen.SETTINGS, "Настройки", Icons.Default.Settings),
        NavItem(Screen.PROFILES, "Профили", Icons.Default.Group),
    )
    val width by animateDpAsState(if (state.navExpanded) 220.dp else 88.dp, tween(280))

    Column(
        Modifier.width(width).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            val selected = state.screen == item.screen
            val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

            Surface(
                onClick = { state.screen = item.screen },
                color = bg,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
            ) {
                if (state.navExpanded) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(item.icon, null, tint = fg)
                        Text(item.label, color = fg, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Column(
                        Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(item.icon, null, tint = fg)
                        Text(item.label, color = fg, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { state.navExpanded = !state.navExpanded }) {
            Icon(if (state.navExpanded) Icons.Default.ChevronLeft else Icons.Default.ChevronRight, "Свернуть меню")
        }
    }
}

@Composable
private fun Content(state: LauncherState, seed: Color, onSeed: (Color) -> Unit) {
    AnimatedContent(
        targetState = state.screen,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(320)) { it / 24 })
                .togetherWith(fadeOut(tween(160)))
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(state)
            Screen.LIBRARY -> LibraryScreen(state)
            Screen.NEWS -> NewsScreen()
            Screen.SETTINGS -> SettingsScreen(state, seed, onSeed)
            Screen.PROFILES -> ProfilesScreen(state)
        }
    }
}

@Composable
private fun animateDpAsState(target: androidx.compose.ui.unit.Dp, spec: androidx.compose.animation.core.AnimationSpec<androidx.compose.ui.unit.Dp>) =
    androidx.compose.animation.core.animateDpAsState(target, spec)
