package com.beammental.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        ink = Color(0xFF110F08), ink2 = Color(0xFF1A170F), card = Color(0xFF211D12),
        line = Color(0xFF3A331F), fog = Color(0xFFAEA593), mist = Color(0xFFF1ECDE),
        accent = Color(0xFF8FBF9A), accentInk = Color(0xFF0D160F),
        wave = listOf(Color(0xFF8FBF9A), Color(0xFF5E8F6B), Color(0xFFBBD4AC), Color(0xFFD9C48E)),
    ),
    BeamThemeDef(
        key = "ocean", label = "Oceán",
        ink = Color(0xFF040A16), ink2 = Color(0xFF091428), card = Color(0xFF0E1D38),
        line = Color(0xFF1F3A66), fog = Color(0xFF93AACA), mist = Color(0xFFEDF5FF),
        accent = Color(0xFF4FC3FF), accentInk = Color(0xFF04121F),
        wave = listOf(Color(0xFF0044FF), Color(0xFF1467E3), Color(0xFF0099FF), Color(0xFF52C5FF)),
    ),
    BeamThemeDef(
        key = "noc", label = "Noc",
        ink = Color(0xFF08080B), ink2 = Color(0xFF101015), card = Color(0xFF17171E),
        line = Color(0xFF25252F), fog = Color(0xFF8E8E99), mist = Color(0xFFF0F0F4),
        accent = Color(0xFFEDE8DC), accentInk = Color(0xFF08080B),
        wave = listOf(Color(0xFF4A4A5C), Color(0xFF2A2A36), Color(0xFF63637A), Color(0xFF17171E)),
    ),
    BeamThemeDef(
        key = "ametyst", label = "Ametyst",
        ink = Color(0xFF110B1E), ink2 = Color(0xFF191130), card = Color(0xFF22173E),
        line = Color(0xFF3B2B62), fog = Color(0xFFAA9CCB), mist = Color(0xFFF0EBFA),
        accent = Color(0xFFA98BFF), accentInk = Color(0xFF130C22),
        wave = listOf(Color(0xFF7C5CFC), Color(0xFF4A2E9E), Color(0xFFB18CFF), Color(0xFFD6C6FF)),
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

/* One shared type scale: tight tracking on headings (Apple-style display
 * cut), generous line height on body, medium-weight labels for chips and
 * pills. Screens that previously hardcoded sizes migrate to these roles. */
private val BeamTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.5.sp,
    ),
)

private val BeamShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

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
        typography = BeamTypography,
        shapes = BeamShapes,
        content = content,
    )
}
