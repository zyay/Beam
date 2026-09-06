package com.beammental.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object BeamColors {
    val Ink = Color(0xFF060607)
    val Ink2 = Color(0xFF0B0B0D)
    val Card = Color(0x14FFFFFF)      // white 8%
    val Line = Color(0x14FFFFFF)      // white 8%
    val Fog = Color(0xFF8B8B94)
    val Mist = Color(0xFFE8E8EC)
}

private val BeamScheme = darkColorScheme(
    primary = Color.White,
    secondary = BeamColors.Mist,
    tertiary = BeamColors.Fog,
    background = BeamColors.Ink,
    surface = BeamColors.Ink2,
    onBackground = BeamColors.Mist,
    onSurface = BeamColors.Mist,
    outline = BeamColors.Line,
)

@Composable
fun BeamTheme(content: @Composable () -> Unit) {
    // the app is dark-only by design; ignore system light theme
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = BeamScheme, content = content)
}
