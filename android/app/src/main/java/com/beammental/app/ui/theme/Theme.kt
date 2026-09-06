package com.beammental.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

data class BeamThemeDef(
    val key: String,
    val label: String,
    val ink: Color,
    val ink2: Color,
    val card: Color,
    val line: Color,
    val fog: Color,
    val mist: Color,
    val accent: Color,
    val accentInk: Color,
    val wave: List<Color>,
)

val BEAM_THEMES = listOf(
    BeamThemeDef(
        key = "salvia", label = "Šalvia",
        ink = Color(0xFF16130E), ink2 = Color(0xFF1D1913), card = Color(0xFF252019),
        line = Color(0xFF3B3428), fog = Color(0xFFA79F91), mist = Color(0xFFEDE8DC),
        accent = Color(0xFF7FAE8B), accentInk = Color(0xFF101912),
        wave = listOf(Color(0xFF7FAE8B), Color(0xFF4E7A5C), Color(0xFFA6C8A0), Color(0xFFD8E4CE)),
    ),
    BeamThemeDef(
        key = "ocean", label = "Oceán",
        ink = Color(0xFF060D1A), ink2 = Color(0xFF0B1526), card = Color(0xFF0F1C33),
        line = Color(0xFF22365A), fog = Color(0xFF8FA6C4), mist = Color(0xFFEAF2FC),
        accent = Color(0xFF52C5FF), accentInk = Color(0xFF051523),
        wave = listOf(Color(0xFF1467E3), Color(0xFF0044FF), Color(0xFF0099FF), Color(0xFF52C5FF)),
    ),
    BeamThemeDef(
        key = "noc", label = "Noc",
        ink = Color(0xFF0A0A0C), ink2 = Color(0xFF131317), card = Color(0xFF1A1A20),
        line = Color(0xFF2A2A32), fog = Color(0xFF8B8B93), mist = Color(0xFFEDEDF0),
        accent = Color(0xFFEDE8DC), accentInk = Color(0xFF0A0A0C),
        wave = listOf(Color(0xFF5A5A66), Color(0xFF26262E), Color(0xFF3A3A44), Color(0xFF14141A)),
    ),
    BeamThemeDef(
        key = "ametyst", label = "Ametyst",
        ink = Color(0xFF150F22), ink2 = Color(0xFF1C1530), card = Color(0xFF241B3D),
        line = Color(0xFF3A2D5C), fog = Color(0xFFA79BC6), mist = Color(0xFFEEE9F8),
        accent = Color(0xFFA78BFA), accentInk = Color(0xFF150F22),
        wave = listOf(Color(0xFF7C5CFC), Color(0xFF4A2E9E), Color(0xFF9F7CFF), Color(0xFFC9B8FF)),
    ),
)

fun beamThemeByKey(key: String?): BeamThemeDef =
    BEAM_THEMES.firstOrNull { it.key == key } ?: BEAM_THEMES[0]

/** Live palette — every read subscribes to [current], so switching themes
 *  recomposes whatever is on screen. "Sage" slots mean "accent". */
object BeamColors {
    var current by mutableStateOf(BEAM_THEMES[0])
        private set

    val Ink: Color get() = current.ink
    val Ink2: Color get() = current.ink2
    val Card: Color get() = current.card
    val Line: Color get() = current.line
    val Fog: Color get() = current.fog
    val Mist: Color get() = current.mist
    val Sage: Color get() = current.accent
    val SageInk: Color get() = current.accentInk

    fun apply(theme: BeamThemeDef) {
        current = theme
    }
}

@Composable
fun BeamTheme(content: @Composable () -> Unit) {
    // the app is dark-only by design; ignore system light theme
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BeamColors.Sage,
            onPrimary = BeamColors.SageInk,
            secondary = BeamColors.Mist,
            tertiary = BeamColors.Fog,
            background = BeamColors.Ink,
            surface = BeamColors.Ink2,
            onBackground = BeamColors.Mist,
            onSurface = BeamColors.Mist,
            outline = BeamColors.Line,
        ),
        content = content,
    )
}
