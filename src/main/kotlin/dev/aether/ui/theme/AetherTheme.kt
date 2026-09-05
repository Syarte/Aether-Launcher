package dev.aether.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Радиусы из макета: карточки 20dp, крупные поверхности и диалоги 28dp. */
private val AetherShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Типографика M3, шкала совпадает с размерами в макете. */
private val AetherTypography = Typography(
    displaySmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
)

/**
 * Плавная смена темы: каждая роль цвета анимируется отдельно,
 * поэтому переключение светлая/тёмная не «моргает».
 */
@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = tween<Color>(durationMillis = 300)
    @Composable fun a(c: Color) = animateColorAsState(c, spec).value
    return copy(
        primary = a(primary), onPrimary = a(onPrimary),
        primaryContainer = a(primaryContainer), onPrimaryContainer = a(onPrimaryContainer),
        secondaryContainer = a(secondaryContainer), onSecondaryContainer = a(onSecondaryContainer),
        background = a(background), onBackground = a(onBackground),
        surface = a(surface), onSurface = a(onSurface),
        surfaceVariant = a(surfaceVariant), onSurfaceVariant = a(onSurfaceVariant),
        surfaceContainerLow = a(surfaceContainerLow), surfaceContainer = a(surfaceContainer),
        surfaceContainerHigh = a(surfaceContainerHigh), surfaceContainerHighest = a(surfaceContainerHighest),
        outline = a(outline), outlineVariant = a(outlineVariant),
    )
}

@Composable
fun AetherTheme(
    seed: Color,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DynamicColor.scheme(seed, dark).animated(),
        typography = AetherTypography,
        shapes = AetherShapes,
        content = content,
    )
}
