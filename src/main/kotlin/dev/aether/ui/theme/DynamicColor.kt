package dev.aether.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.*

/**
 * Генерация схемы Material Design 3 из seed-цвета.
 *
 * Реализована та же математика, что и в приложенном макете: перцептивное
 * пространство OKLab/OKLCH (Björn Ottosson). Тон берётся из seed-цвета,
 * а светлота и хрома задаются фиксированной шкалой ролей — за счёт этого
 * пары surface/onSurface сохраняют контраст при любом seed.
 *
 * Альтернатива для полной совместимости с эталонными палитрами Google —
 * библиотека material-color-utilities (HCT); см. README.
 */
object DynamicColor {

    private fun srgbToLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun linearToSrgb(c: Double): Double {
        val v = c.coerceIn(0.0, 1.0)
        return if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055
    }

    private fun rgbToOklab(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
        val lr = srgbToLinear(r); val lg = srgbToLinear(g); val lb = srgbToLinear(b)
        val l = 0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb
        val m = 0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb
        val s = 0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb
        val l_ = cbrt(l); val m_ = cbrt(m); val s_ = cbrt(s)
        return Triple(
            0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
            1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
            0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_,
        )
    }

    private fun oklabToColor(lightness: Double, a: Double, b: Double): Color {
        val l_ = lightness + 0.3963377774 * a + 0.2158037573 * b
        val m_ = lightness - 0.1055613458 * a - 0.0638541728 * b
        val s_ = lightness - 0.0894841775 * a - 1.2914855480 * b
        val l = l_ * l_ * l_; val m = m_ * m_ * m_; val s = s_ * s_ * s_
        val lr = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val lg = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val lb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return Color(
            linearToSrgb(lr).toFloat(),
            linearToSrgb(lg).toFloat(),
            linearToSrgb(lb).toFloat(),
        )
    }

    private fun oklch(lightness: Double, chroma: Double, hueDeg: Double): Color {
        val h = hueDeg * PI / 180.0
        return oklabToColor(lightness, chroma * cos(h), chroma * sin(h))
    }

    fun hueOf(seed: Color): Double {
        val (_, a, b) = rgbToOklab(seed.red.toDouble(), seed.green.toDouble(), seed.blue.toDouble())
        return atan2(b, a) * 180.0 / PI
    }

    fun scheme(seed: Color, dark: Boolean): ColorScheme {
        val h = hueOf(seed)
        val hVariant = h + 15
        val hTertiary = h + 65
        fun c(l: Double, chroma: Double, hue: Double = h) = oklch(l, chroma, hue)

        return if (dark) darkColorScheme(
            primary = c(0.82, 0.12),
            onPrimary = c(0.18, 0.06),
            primaryContainer = c(0.32, 0.10),
            onPrimaryContainer = c(0.90, 0.06),
            secondary = c(0.80, 0.05, hVariant),
            onSecondary = c(0.20, 0.03, hVariant),
            secondaryContainer = c(0.30, 0.05, hVariant),
            onSecondaryContainer = c(0.90, 0.04, hVariant),
            tertiary = c(0.80, 0.09, hTertiary),
            onTertiary = c(0.20, 0.05, hTertiary),
            tertiaryContainer = c(0.30, 0.08, hTertiary),
            onTertiaryContainer = c(0.90, 0.05, hTertiary),
            background = c(0.14, 0.006),
            onBackground = c(0.92, 0.01),
            surface = c(0.14, 0.006),
            onSurface = c(0.92, 0.01),
            surfaceVariant = c(0.24, 0.009),
            onSurfaceVariant = c(0.78, 0.02),
            surfaceContainerLowest = c(0.10, 0.006),
            surfaceContainerLow = c(0.16, 0.007),
            surfaceContainer = c(0.19, 0.008),
            surfaceContainerHigh = c(0.24, 0.009),
            surfaceContainerHighest = c(0.29, 0.010),
            outline = c(0.60, 0.02),
            outlineVariant = c(0.32, 0.012),
            scrim = Color(0f, 0f, 0f),
            error = oklch(0.70, 0.17, 27.0),
            onError = oklch(0.20, 0.08, 27.0),
            errorContainer = oklch(0.32, 0.13, 27.0),
            onErrorContainer = oklch(0.90, 0.06, 27.0),
        ) else lightColorScheme(
            primary = c(0.47, 0.15),
            onPrimary = Color.White,
            primaryContainer = c(0.90, 0.08),
            onPrimaryContainer = c(0.18, 0.06),
            secondary = c(0.50, 0.06, hVariant),
            onSecondary = Color.White,
            secondaryContainer = c(0.90, 0.04, hVariant),
            onSecondaryContainer = c(0.20, 0.04, hVariant),
            tertiary = c(0.50, 0.11, hTertiary),
            onTertiary = Color.White,
            tertiaryContainer = c(0.90, 0.07, hTertiary),
            onTertiaryContainer = c(0.20, 0.05, hTertiary),
            background = c(0.985, 0.004),
            onBackground = c(0.16, 0.01),
            surface = c(0.985, 0.004),
            onSurface = c(0.16, 0.01),
            surfaceVariant = c(0.91, 0.007),
            onSurfaceVariant = c(0.40, 0.02),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = c(0.96, 0.005),
            surfaceContainer = c(0.94, 0.006),
            surfaceContainerHigh = c(0.91, 0.007),
            surfaceContainerHighest = c(0.88, 0.008),
            outline = c(0.50, 0.02),
            outlineVariant = c(0.82, 0.012),
            scrim = Color(0f, 0f, 0f),
            error = oklch(0.51, 0.19, 27.0),
            onError = Color.White,
            errorContainer = oklch(0.90, 0.07, 27.0),
            onErrorContainer = oklch(0.20, 0.09, 27.0),
        )
    }
}
