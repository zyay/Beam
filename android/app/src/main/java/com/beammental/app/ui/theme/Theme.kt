package com.beammental.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Pi-inspired warm dark palette: soft charcoal-beige base, sage green accents. */
object BeamColors {
    val Ink = Color(0xFF16130E)      // warm dark background
    val Ink2 = Color(0xFF1D1913)     // elevated surface
    val Card = Color(0xFF252019)     // warm card
    val Line = Color(0xFF3B3428)     // warm hairline border
    val Fog = Color(0xFFA79F91)     // warm gray text
    val Mist = Color(0xFFEDE8DC)    // warm off-white text
    val Sage = Color(0xFF7FAE8B)    // Pi green, adapted for dark
    val SageInk = Color(0xFF101912) // text/icons on sage
}

private val BeamScheme = darkColorScheme(
    primary = BeamColors.Sage,
    onPrimary = BeamColors.SageInk,
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
