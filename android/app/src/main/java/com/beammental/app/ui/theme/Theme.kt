package com.beammental.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.R
import com.beammental.app.ui.effects.EffectClock

/**
 * Beam's design language, taken from libraries.dev.
 *
 * Every theme shares one neutral dark base (#121212 family) and differs only in
 * accent hue and border-beam palette — the same model libraries.dev uses for its
 * `colorVariant` axis. Per-theme inks are deliberately gone: they were what made
 * the old build read as coloured fog instead of a calm dark room.
 */
data class BeamThemeDef(
    val key: String,
    val label: String,
    /* neutral surfaces — identical across themes */
    val ink: Color,
    val ink2: Color,
    val card: Color,
    val cardPressed: Color,
    val cardHover: Color,
    val line: Color,
    val glow: Color,
    /* text scale */
    val mist: Color,
    val text2: Color,
    val text3: Color,
    val text4: Color,
    val fog: Color,
    /* accent + semantic */
    val accent: Color,
    val accentInk: Color,
    val accentHover: Color,
    val accentActive: Color,
    val accentSoft: Color,
    val danger: Color,
    val dangerInk: Color,
    val warn: Color,
    val ok: Color,
    /* effect ramps */
    val wave: List<Color>,
    val beam: List<Color>,
)

private val INK = Color(0xFF121212)
private val INK2 = Color(0xFF101010)
private val CARD = Color(0xFF1D1D1D)
private val CARD_PRESSED = Color(0xFF1C1C1C)
private val CARD_HOVER = Color(0xFF181818)
private val BUTTON = Color(0xFF2A2A2A)
private val BUTTON_PRESSED = Color(0xFF262626)
private val LINE = Color(0x852C2F36)          // rgba(44,47,54,0.52)
private val GLOW = Color(0x05FFFFFF)          // inset 0 0 50px rgba(255,255,255,0.02)
private val MIST = Color(0xFFF8F8F8)
private val TEXT2 = Color(0xFFEDEDED)
private val TEXT3 = Color(0xFFC9C9C9)
private val TEXT4 = Color(0xFF9E9E9E)
private val FOG = Color(0xFF767676)
private val CHROME = Color(0xFFE6E9EF)         // specular stop of the chrome ramp
private val HAIRLINE = Color(0x1AFFFFFF)       // 1px panel edge, 10% white
private val DANGER = Color(0xFFFF6E82)
private val DANGER_INK = Color(0xFFFFC4CB)
private val WARN = Color(0xFFFFB454)
private val OK = Color(0xFF6EE7B7)

/* libraries.dev border-beam palettes (beam-spec.json) */
private val BEAM_COLORFUL_FULL = listOf(
    Color(0xFFFF3264), Color(0xFF288CFF), Color(0xFF32C850), Color(0xFF1EB9AA),
    Color(0xFF6446FF), Color(0xFF288CFF), Color(0xFFFF7828), Color(0xFFF032B4),
    Color(0xFFB428F0),
)
private val BEAM_OCEAN = listOf(
    Color(0xFF0044FF), Color(0xFF1467E3), Color(0xFF0099FF), Color(0xFF52C5FF),
    Color(0xFF7CD4FF), Color(0xFF0071FC), Color(0xFF1A7DFF), Color(0xFF52C5FF),
    Color(0xFF0099FF),
)
private val BEAM_SUNSET = listOf(
    Color(0xFFFF8A4C), Color(0xFFFF6E82), Color(0xFFF050B4), Color(0xFFB428F0),
    Color(0xFFFFC46B), Color(0xFFFF7828), Color(0xFFFF3264), Color(0xFFF050B4),
    Color(0xFFFF8A4C),
)
private val BEAM_MONO = listOf(
    Color(0xFFB4B4B4), Color(0xFF8C8C8C), Color(0xFFA0A0A0), Color(0xFF828282),
    Color(0xFFAAAAAA), Color(0xFF969696), Color(0xFFBEBEBE), Color(0xFF8C8C8C),
    Color(0xFFB4B4B4),
)

val BEAM_THEMES = listOf(
    BeamThemeDef(
        key = "beam", label = "Beam",
        ink = INK, ink2 = INK2, card = CARD, cardPressed = CARD_PRESSED, cardHover = CARD_HOVER,
        line = LINE, glow = GLOW,
        mist = MIST, text2 = TEXT2, text3 = TEXT3, text4 = TEXT4, fog = FOG,
        accent = Color(0xFF0071FC), accentInk = Color(0xFFFFFFFF),
        accentHover = Color(0xFF1A7DFF), accentActive = Color(0xFF0063E0),
        accentSoft = Color(0xFF7CD4FF),
        danger = DANGER, dangerInk = DANGER_INK, warn = WARN, ok = OK,
        wave = listOf(Color(0xFF0071FC), Color(0xFF1A7DFF), Color(0xFF7CD4FF), Color(0xFF2A2A2A)),
        beam = BEAM_COLORFUL_FULL,
    ),
    BeamThemeDef(
        key = "ocean", label = "Oceán",
        ink = INK, ink2 = INK2, card = CARD, cardPressed = CARD_PRESSED, cardHover = CARD_HOVER,
        line = LINE, glow = GLOW,
        mist = MIST, text2 = TEXT2, text3 = TEXT3, text4 = TEXT4, fog = FOG,
        accent = Color(0xFF4FC3FF), accentInk = Color(0xFF04121F),
        accentHover = Color(0xFF6BCDFF), accentActive = Color(0xFF2FA9E8),
        accentSoft = Color(0xFF9ADCFF),
        danger = DANGER, dangerInk = DANGER_INK, warn = WARN, ok = OK,
        wave = listOf(Color(0xFF0044FF), Color(0xFF1467E3), Color(0xFF0099FF), Color(0xFF52C5FF)),
        beam = BEAM_OCEAN,
    ),
    BeamThemeDef(
        key = "zapad", label = "Západ",
        ink = INK, ink2 = INK2, card = CARD, cardPressed = CARD_PRESSED, cardHover = CARD_HOVER,
        line = LINE, glow = GLOW,
        mist = MIST, text2 = TEXT2, text3 = TEXT3, text4 = TEXT4, fog = FOG,
        accent = Color(0xFFFF8A4C), accentInk = Color(0xFF2A1206),
        accentHover = Color(0xFFFF9D68), accentActive = Color(0xFFE8743A),
        accentSoft = Color(0xFFFFC46B),
        danger = DANGER, dangerInk = DANGER_INK, warn = WARN, ok = OK,
        wave = listOf(Color(0xFFFF8A4C), Color(0xFFFF6E82), Color(0xFFFFC46B), Color(0xFFB4525E)),
        beam = BEAM_SUNSET,
    ),
    BeamThemeDef(
        key = "noc", label = "Noc",
        ink = INK, ink2 = INK2, card = CARD, cardPressed = CARD_PRESSED, cardHover = CARD_HOVER,
        line = LINE, glow = GLOW,
        mist = MIST, text2 = TEXT2, text3 = TEXT3, text4 = TEXT4, fog = FOG,
        accent = Color(0xFFEDEDED), accentInk = Color(0xFF121212),
        accentHover = Color(0xFFFFFFFF), accentActive = Color(0xFFC9C9C9),
        accentSoft = Color(0xFFB5B5B5),
        danger = DANGER, dangerInk = DANGER_INK, warn = WARN, ok = OK,
        wave = listOf(Color(0xFF4A4A52), Color(0xFF2A2A30), Color(0xFF63636C), Color(0xFF1C1C20)),
        beam = BEAM_MONO,
    ),
)

fun beamThemeByKey(key: String?): BeamThemeDef =
    BEAM_THEMES.firstOrNull { it.key == key } ?: BEAM_THEMES[0]

/** Live palette — every read subscribes to [current], so switching themes
 *  recomposes whatever is on screen. "Sage" slots are kept as aliases for
 *  "accent" so existing screens keep compiling during the migration. */
object BeamColors {
    var current by mutableStateOf(BEAM_THEMES[0])
        private set

    val Ink: Color get() = current.ink
    val Ink2: Color get() = current.ink2
    val Card: Color get() = current.card
    val CardPressed: Color get() = current.cardPressed
    val CardHover: Color get() = current.cardHover
    /** Neutral control fills — libraries.dev's #2a2a2a button, #262626 pressed. */
    val Button: Color get() = BUTTON
    val ButtonPressed: Color get() = BUTTON_PRESSED
    val Line: Color get() = current.line
    val Glow: Color get() = current.glow
    val Mist: Color get() = current.mist
    val Text2: Color get() = current.text2
    val Text3: Color get() = current.text3
    val Text4: Color get() = current.text4
    val Fog: Color get() = current.fog
    val Sage: Color get() = current.accent
    val SageInk: Color get() = current.accentInk
    val Accent: Color get() = current.accent
    val AccentInk: Color get() = current.accentInk
    val AccentHover: Color get() = current.accentHover
    val AccentActive: Color get() = current.accentActive
    val AccentSoft: Color get() = current.accentSoft
    val Danger: Color get() = current.danger
    val DangerInk: Color get() = current.dangerInk
    val Warn: Color get() = current.warn
    val Ok: Color get() = current.ok
    val Wave: List<Color> get() = current.wave
    val BeamPalette: List<Color> get() = current.beam

    /** Specular stop of the chrome ramp and the 1px panel edge. Neutral across
     *  themes — the metal's colour comes from the accent stop in the ramp. */
    val Chrome: Color get() = CHROME
    val Hairline: Color get() = HAIRLINE

    fun apply(theme: BeamThemeDef) {
        current = theme
    }
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/* One shared type scale. libraries.dev sets its hero at -0.21px tracking; the
 * same tight cut is applied to headings here, with generous line height on
 * body so long Slovak messages stay readable. */
private val BeamTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp,
    ),
)

private val BeamShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BeamTheme(content: @Composable () -> Unit) {
    // the app is dark-only by design; ignore system light theme
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BeamColors.Accent,
            onPrimary = BeamColors.AccentInk,
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
        content = {
            EffectClock.Host()
            content()
        },
    )
}
