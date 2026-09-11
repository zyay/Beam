package com.beammental.app.ui.effects

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Border beam — Beam's port of libraries.dev `border-beam` 1.3.0, rotate
 * family (`sm` / `md`), transcribed from `beam-spec.json` and the upstream iOS
 * port's layer order.
 *
 * The web effect is three composited layers over the same rounded rect: an
 * inner colour glow, a 1px stroke ring of colour blobs masked by a rotating
 * conic window, and a blurred bloom of that same window. Compose has no shader
 * here, so each layer becomes what a DrawScope can express exactly:
 *
 *  - inner glow  → a radial gradient anchored at the beam head, clipped inside
 *  - stroke ring → the palette as a sweep gradient, plus the white conic
 *    highlight rotated by the beam angle and composited additively
 *  - bloom       → the same highlight stroked twice, wide and faint, which is
 *    what a blur of a 1px rim actually looks like
 *
 * Two deviations, both deliberate. The hue-rotate ping-pong uses HSV rather
 * than the CSS `feColorMatrix` linearRGB model — same ±30° drift, a fraction of
 * the cost, and it never leaves gamut. And the bloom is fake-blurred strokes
 * rather than a `RenderEffect`, so it works on every API level Beam supports.
 */

/** `sizePresets` + `sizeThemePresets[.].dark` from beam-spec.json. */
enum class BeamSize(
    val radius: Dp,
    val border: Dp,
    val strokeOpacity: Float,
    val innerOpacity: Float,
    val bloomOpacity: Float,
    val innerShadowAlpha: Float,
    val innerShadowBlur: Dp,
) {
    SM(32.dp, 1.dp, 0.46f, 0.24f, 0.38f, 0.30f, 5.dp),
    MD(16.dp, 1.dp, 0.26f, 0.42f, 0.24f, 0.27f, 9.dp),
}

/* defaults.duration.rotate and defaults.rotateHueShiftPeriod */
private const val ROTATE_SECONDS = 1.96f
private const val HUE_PERIOD_SECONDS = 12f

/* rotate.whiteGradientStops.dark — the conic window. Transparent either side
 * of a bright head at 66%, which is what makes the rim read as one travelling
 * beam instead of a spinning colour wheel. */
private val WHITE_WINDOW = listOf(
    0.00f to 0f, 0.54f to 0f, 0.57f to 0.10f, 0.60f to 0.30f, 0.63f to 0.60f,
    0.66f to 0.75f, 0.69f to 0.60f, 0.72f to 0.30f, 0.75f to 0.10f,
    0.78f to 0f, 1.00f to 0f,
)

/* rotate.bloomGradientStops.dark — narrower and hotter than the window. */
private val BLOOM_WINDOW = listOf(
    0.00f to 0f, 0.58f to 0f, 0.62f to 0.03f, 0.65f to 0.08f, 0.67f to 0.20f,
    0.69f to 0.45f, 0.70f to 0.85f, 0.715f to 0.45f, 0.73f to 0.20f,
    0.75f to 0.08f, 0.78f to 0.03f, 0.82f to 0f, 1.00f to 0f,
)

/**
 * The travelling rim.
 *
 * @param active fades the beam in over 0.6 s and out over 0.5 s
 *   (`defaults.fadeInSeconds` / `fadeOutSeconds`) rather than popping it — a
 *   card that starts beaming the instant it appears reads as a glitch.
 * @param strength multiplies every layer's opacity; use it to calm the beam
 *   down on long surfaces where the full-strength rim would compete with text.
 * @param palette overrides the theme's beam ramp, e.g. a crisis card running
 *   the danger hue instead of the theme accent.
 */
@Composable
fun Modifier.borderBeam(
    size: BeamSize = BeamSize.MD,
    shape: Shape = RoundedCornerShape(size.radius),
    active: Boolean = true,
    strength: Float = 1f,
    hueRange: Float = 30f,
    palette: List<Color> = BeamColors.BeamPalette,
): Modifier {
    val fade by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (active) 600 else 500),
        label = "borderBeamFade",
    )
    val themeKey = BeamColors.current.key
    val base = remember(themeKey, palette) { palette }

    return this.drawWithContent {
        drawContent()
        if (fade <= 0.001f || strength <= 0f) return@drawWithContent

        val t = if (EffectClock.reducedMotion) {
            EffectClock.FROZEN_T.toDouble()
        } else {
            EffectClock.t
        }
        val progress = ((t / ROTATE_SECONDS) % 1.0).toFloat()
        val turn = progress * 360f
        val hue = hueShift(t.toFloat(), hueRange)
        val ramp = if (hue == 0f) base else base.map { it.shiftHue(hue) }

        val outline = Path().apply {
            addOutline(shape.createOutline(this@drawWithContent.size, layoutDirection, this@drawWithContent))
        }
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val borderPx = min(size.border.toPx(), 2.6f)
        val head = rimPoint(c, this.size.width / 2f, this.size.height / 2f, headAngle(progress))
        val master = fade * strength

        // inner colour glow, brightest where the beam head is
        drawPath(
            outline,
            brush = Brush.radialGradient(
                colors = listOf(
                    ramp[progress.stopIndex(ramp.size)].copy(alpha = 0.42f * size.innerOpacity),
                    Color.Transparent,
                ),
                center = head,
                radius = (this.size.minDimension * 0.85f),
            ),
            alpha = master * size.innerOpacity,
        )
        // inset rim light: the "innerShadow rgba(255,255,255,0.27)" of the spec,
        // faked with three inset strokes standing in for its blur
        val shadow = Color.White.copy(alpha = size.innerShadowAlpha * master)
        drawPath(outline, shadow, style = Stroke(borderPx * 1.2f))
        drawPath(outline, shadow.copy(alpha = shadow.alpha * 0.34f), style = Stroke(borderPx * 3f))
        drawPath(outline, shadow.copy(alpha = shadow.alpha * 0.12f), style = Stroke(size.innerShadowBlur.toPx()))

        // stroke ring: palette under the rotating white window
        drawPath(
            outline,
            brush = Brush.sweepGradient(ramp, center = c),
            style = Stroke(borderPx),
            alpha = master * size.strokeOpacity,
        )
        rotate(turn, pivot = c) {
            drawPath(
                outline,
                brush = conicWindow(WHITE_WINDOW, c, Color.White),
                style = Stroke(borderPx * 1.8f),
                alpha = master,
                blendMode = BlendMode.Plus,
            )
            // bloom: wide + faint passes of the hotter window
            drawPath(
                outline,
                brush = conicWindow(BLOOM_WINDOW, c, ramp[progress.stopIndex(ramp.size)]),
                style = Stroke(borderPx * 4f),
                alpha = master * size.bloomOpacity * 0.5f,
                blendMode = BlendMode.Plus,
            )
            drawPath(
                outline,
                brush = conicWindow(BLOOM_WINDOW, c, Color.White),
                style = Stroke(borderPx * 9f),
                alpha = master * size.bloomOpacity * 0.22f,
                blendMode = BlendMode.Plus,
            )
        }
    }
}

/** ±[hueRange] ping-pong over [HUE_PERIOD_SECONDS] (`rotateHueShiftDegrees`). */
private fun hueShift(t: Float, hueRange: Float): Float {
    if (hueRange == 0f) return 0f
    val phase = (t / HUE_PERIOD_SECONDS) % 1f
    val pingPong = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
    return -hueRange + 2f * hueRange * pingPong
}

/** Where the beam head sits on the sweep: the window peaks at 66%. */
private fun headAngle(progress: Float): Float = (progress + 0.66f) * 360f

private fun Float.stopIndex(n: Int): Int = ((this * n).toInt()).coerceIn(0, n - 1)

/** A point on the rect boundary at [angleDeg], pulled in so the glow's centre
 *  stays inside the rounded corner. */
private fun rimPoint(c: Offset, hw: Float, hh: Float, angleDeg: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    val dx = cos(a).toFloat()
    val dy = sin(a).toFloat()
    val sx = if (abs(dx) < 1e-4f) Float.MAX_VALUE else (hw * 0.94f) / abs(dx)
    val sy = if (abs(dy) < 1e-4f) Float.MAX_VALUE else (hh * 0.94f) / abs(dy)
    val s = min(sx, sy)
    return Offset(c.x + dx * s, c.y + dy * s)
}

/** The conic window as a static sweep gradient. Rotation happens on the draw
 *  (`rotate(turn, pivot)`), never on the stops: re-sorting shifted stops breaks
 *  the wrap at 0/1 and scrambles the beam head. */
private fun conicWindow(
    stops: List<Pair<Float, Float>>,
    center: Offset,
    tint: Color,
): Brush = Brush.sweepGradient(
    *stops.map { (pos, alpha) -> pos to tint.copy(alpha = alpha) }.toTypedArray(),
    center = center,
)

private fun Color.shiftHue(degrees: Float): Color {
    val hsv = floatArrayOf(0f, 0f, 0f)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv))
}
