package com.dayledger.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F5C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EBE2),
    onPrimaryContainer = Color(0xFF08352A),
    secondary = Color(0xFFB0653A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7E3D6),
    onSecondaryContainer = Color(0xFF3B1C0B),
    tertiary = Color(0xFF6C5C8E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF7F0),
    onBackground = Color(0xFF1D1B17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1B17),
    surfaceVariant = Color(0xFFEFE9DE),
    onSurfaceVariant = Color(0xFF5C564C),
    outline = Color(0xFF918A7E),
    outlineVariant = Color(0xFFDBD4C8),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCBB6),
    onPrimary = Color(0xFF04382B),
    primaryContainer = Color(0xFF145040),
    onPrimaryContainer = Color(0xFFCFEDE1),
    secondary = Color(0xFFE0A276),
    onSecondary = Color(0xFF3B1C0B),
    secondaryContainer = Color(0xFF55301A),
    onSecondaryContainer = Color(0xFFF7E3D6),
    tertiary = Color(0xFFC4B4E8),
    onTertiary = Color(0xFF2A1F44),
    background = Color(0xFF131210),
    onBackground = Color(0xFFEDE7DD),
    surface = Color(0xFF1B1916),
    onSurface = Color(0xFFEDE7DD),
    surfaceVariant = Color(0xFF2A2722),
    onSurfaceVariant = Color(0xFFCFC7BA),
    outline = Color(0xFF7A7368),
    outlineVariant = Color(0xFF3A362F),
    error = Color(0xFFF2B8B5),
)

object Ink {
    val money = Color(0xFFD97742)
    val time = Color(0xFF4A7FB5)
    val diet = Color(0xFF5A9E6F)
    val event = Color(0xFF8A6FB8)
    val good = Color(0xFF2F8F6B)
    val warn = Color(0xFFC98A2E)
    val bad = Color(0xFFB8544A)

    fun score(v: Float?): Color = when {
        v == null -> Color(0xFF9A938A)
        v >= 90f -> Color(0xFF2E8B57)
        v >= 80f -> Color(0xFF5A9E6F)
        v >= 70f -> Color(0xFFC9A227)
        v >= 60f -> Color(0xFFD98A3D)
        else -> Color(0xFFBF5B4E)
    }
}

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun DayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}
