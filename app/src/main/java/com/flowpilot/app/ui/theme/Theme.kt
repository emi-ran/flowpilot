package com.flowpilot.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Restrained neutral Material 3 palette matching the supplied Stitch design.
// Dark-first; no gradients, no purple, modest accents.
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5CFFF),
    onPrimary = Color(0xFF003061),
    primaryContainer = Color(0xFF8AB4F8),
    onPrimaryContainer = Color(0xFF0D4582),
    inversePrimary = Color(0xFF315F9D),
    secondary = Color(0xFFC8C6C5),
    onSecondary = Color(0xFF303030),
    secondaryContainer = Color(0xFF474747),
    onSecondaryContainer = Color(0xFFB6B5B4),
    tertiary = Color(0xFFFBC65E),
    onTertiary = Color(0xFF412D00),
    tertiaryContainer = Color(0xFFDDAB46),
    onTertiaryContainer = Color(0xFF5B4000),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF131313),
    onBackground = Color(0xFFE5E2E1),
    surface = Color(0xFF111111),
    onSurface = Color(0xFFE2E2E8),
    surfaceVariant = Color(0xFF2B2B2B),
    onSurfaceVariant = Color(0xFFC3C6D1),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF424750),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF222222),
    surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF383838),
    surfaceBright = Color(0xFF3E3E3E),
    surfaceDim = Color(0xFF131313),
)

private val FlowPilotShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun FlowPilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        shapes = FlowPilotShapes,
        content = content,
    )
}
