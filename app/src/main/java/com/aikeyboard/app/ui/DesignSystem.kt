package com.aikeyboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design system central do app.
 *
 * Direção visual: Executive Graphite — superfícies profundas, contraste limpo,
 * acento violeta controlado e componentes com ritmo consistente.
 */
object AppColors {
    val Graphite950 = Color(0xFF0A0C11)
    val Graphite900 = Color(0xFF10131A)
    val Graphite850 = Color(0xFF151923)
    val Graphite800 = Color(0xFF1B2030)
    val Graphite700 = Color(0xFF252C3B)
    val Graphite500 = Color(0xFF697386)
    val Graphite300 = Color(0xFFAEB7C8)
    val Graphite100 = Color(0xFFF4F6FA)

    val Accent = Color(0xFF8B7CFF)
    val AccentStrong = Color(0xFF7767F4)
    val AccentSoft = Color(0xFF24213E)
    val Success = Color(0xFF35D399)
    val Warning = Color(0xFFF5C451)
}

private val DarkScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color.White,
    primaryContainer = AppColors.AccentSoft,
    onPrimaryContainer = Color(0xFFE7E3FF),
    secondary = AppColors.Graphite300,
    onSecondary = AppColors.Graphite950,
    background = AppColors.Graphite950,
    onBackground = AppColors.Graphite100,
    surface = AppColors.Graphite900,
    onSurface = AppColors.Graphite100,
    surfaceVariant = AppColors.Graphite850,
    onSurfaceVariant = AppColors.Graphite300,
    outline = AppColors.Graphite700
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6858E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF221A62),
    secondary = Color(0xFF566174),
    onSecondary = Color.White,
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF11131A),
    surface = Color.White,
    onSurface = Color(0xFF11131A),
    surfaceVariant = Color(0xFFF0F2F7),
    onSurfaceVariant = Color(0xFF596274),
    outline = Color(0xFFD8DDEA)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography()

@Composable
fun AIKeyboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) {
            DarkScheme
        } else {
            LightScheme
        },
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        scheme.background,
                        scheme.surfaceVariant.copy(alpha = 0.28f),
                        scheme.background
                    )
                )
            )
    ) {
        content()
    }
}
