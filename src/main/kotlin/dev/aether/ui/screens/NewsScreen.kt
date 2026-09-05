package dev.aether.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Заглушка новостей. Источник — официальный фид Mojang
 * (https://launchercontent.mojang.com/news.json); подключается на этапе 2,
 * см. README. Рекламных и партнёрских блоков в ленте нет по определению.
 */
@Composable
fun NewsScreen() {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Новости", style = MaterialTheme.typography.headlineSmall)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
            Text(
                "Лента официальных новостей Minecraft появится в следующей итерации.",
                Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
