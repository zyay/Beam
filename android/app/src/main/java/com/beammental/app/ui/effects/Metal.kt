package com.beammental.app.ui.effects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.cos

/**
 * Liquid metal — Beam's take on the libraries.dev "Metal" look.
 *
 * A chrome rim is a conic (sweep) gradient built from the live palette: deep
 * shadow → mid steel → bright accent → specular white, twice around. Rotating
 * that ramp is what makes a border read as polished metal catching a moving
 * light instead of as a coloured stroke.
 *
 * Over it travels a second, much narrower band going the other way at an
 * irrational rate ratio — the "wandering halo". Two bands moving against each
 * other is what sells liquid metal; a single rotating gradient reads as a
 * spinner.
 */

/** The chrome ramp, derived from the live palette so each theme gets its own
 *  metal: blue steel for Beam, bright alloy for Oceán, warm brass for Západ,
 *  near-white silver for Noc. */
private fun chromeRamp(): List<Pair<Float, Color>> {
    val deep = BeamColors.Ink2
    val mid = BeamColors.Line
    val bright = BeamColors.Accent
    val spec = BeamColors.Chrome
    return listOf(
        0.00f to deep,
        0.07f to mid,
        0.15f to bright,
        0.21f to spec,
        0.28f to bright,
        0.40f to mid,
        0.50f to deep,
        0.60f to mid,
        0.70f to bright,
        0.76f to spec,
        0.83f to bright,
        0.93f to mid,
        1.00f to deep,
    )
}

/** Narrow specular band for the halo; transparent elsewhere so it composites
 *  additively over the base ramp without dimming it. */
private fun haloRamp(): List<Pair<Float, Color>> = listOf(
    0.00f to Color.Transparent,
    0.30f to Color.Transparent,
    0.42f to Color.White.copy(alpha = 0.22f),
    0.50f to Color.White.copy(alpha = 0.60f),
    0.58f to Color.White.copy(alpha = 0.22f),
    0.70f to Color.Transparent,
    1.00f to Color.Transparent,
)

/**
 * Chrome rim around any [shape].
 *
 * Painted by clipping to the outline, drawing the rotated sweep across an
 * oversized rect, then knocking the interior back out with [fill]. Punching
 * the hole rather than stroking a path is what keeps the ramp even on rounded
 * rectangles — a stroked sweep concentrates at the corners and thins on the
 * flats, which reads as a bad gradient rather than as metal.
 *
 * @param active flares the halo and thickens the rim: press, focus, or "the
 *   model is working".
 */
@Composable
fun Modifier.metalRing(
    shape: Shape = RoundedCornerShape(16.dp),
    ringWidth: Dp = 1.6.dp,
    fill: Color = BeamColors.Card,
    innerBrush: Brush? = null,
    active: Boolean = false,
    halo: Float = 1f,
): Modifier {
    val key = BeamColors.current.key
    val chrome = remember(key) { chromeRamp() }
    val glow = remember(key) { haloRamp() }
    val width = ringWidth
    val boost = if (active) 1.35f else 1f
    val haloAlpha = ((0.45f + 0.55f * halo) * (if (active) 1.35f else 1f)).coerceIn(0f, 1f)

    return this.drawWithCache {
        val inset = width.toPx() * boost
        val outer = Path().apply {
            addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        }
        val innerSize = Size(
            (size.width - inset * 2f).coerceAtLeast(0f),
            (size.height - inset * 2f).coerceAtLeast(0f),
        )
        val inner = Path().apply {
            addOutline(shape.createOutline(innerSize, layoutDirection, this@drawWithCache))
            translate(Offset(inset, inset))
        }
        val c = Offset(size.width / 2f, size.height / 2f)
        val sweep = Brush.sweepGradient(*chrome.toTypedArray(), center = c)
        val haloBrush = Brush.sweepGradient(*glow.toTypedArray(), center = c)
        // oversized so the rotated rect always covers the outline
        val big = Size(size.width * 3f, size.height * 3f)
        val bigTop = Offset(-size.width, -size.height)

        onDrawBehind {
            val a = EffectClock.angle
            clipPath(outer) {
                rotate(a, pivot = c) {
                    drawRect(brush = sweep, topLeft = bigTop, size = big)
                }
                // counter-rotating at a golden-ratio rate so the two bands
                // only rarely line up — that drift is the "wandering" halo
                rotate(-a * 0.618f + 40f, pivot = c) {
                    drawRect(
                        brush = haloBrush,
                        topLeft = bigTop,
                        size = big,
                        alpha = haloAlpha,
                        blendMode = BlendMode.Plus,
                    )
                }
            }
            if (innerBrush != null) drawPath(inner, innerBrush) else drawPath(inner, fill)
        }
    }
}

/**
 * Hairline panel edge: a 1px border plus a specular highlight that slides
 * along the rim as the clock turns. This is the cheap metal, for every card
 * and list row where a full rotating rim would be noise.
 */
@Composable
fun Modifier.metalEdge(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    val key = BeamColors.current.key
    val hairline = BeamColors.Hairline
    return this.drawWithCache {
        val path = Path().apply {
            addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
        }
        val w = 1.dp.toPx()
        val h = size.height
        // Drawn over the content, not behind it. onDrawBehind would paint before
        // anything further along the chain, so the common
        // .metalEdge(shape).background(fill, shape) ordering would cover the rim
        // entirely and every card would lose its chrome edge. Drawing last makes
        // the rim visible wherever it sits in the chain.
        onDrawWithContent {
            drawContent()
            // read here, not above: the sheen must animate without recomposing
            val drift = 0.5f + 0.5f * cos(EffectClock.seconds * 0.35f)
            drawPath(path, hairline, style = Stroke(w))
            drawPath(
                path,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f + 0.14f * drift),
                        Color.White.copy(alpha = 0.03f),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * (0.04f + 0.38f * drift), 0f),
                    end = Offset(size.width * 0.86f, h * 0.68f),
                ),
                style = Stroke(w),
            )
        }
    }
}

/** Dark chrome panel: near-black fill, hairline edge, content inside. */
@Composable
fun MetalPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    fill: Color = BeamColors.Card,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .graphicsLayer {
                val s = if (pressed && onClick != null) 0.985f else 1f
                scaleX = s
                scaleY = s
            }
            .metalEdge(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            ),
    ) {
        Box(
            Modifier
                .matchParentSize()
                .drawWithCache {
                    val p = Path().apply {
                        addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
                    }
                    onDrawBehind { drawPath(p, fill) }
                },
        )
        Box(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * Primary action. Chrome rim that flares while pressed or [busy], over a
 * top-lit interior so the button reads as a machined key rather than as a
 * filled rectangle.
 */
@Composable
fun MetalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
    label: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lit = pressed || busy
    val key = BeamColors.current.key
    val sheen = remember(key) {
        Brush.verticalGradient(listOf(BeamColors.Ink2, BeamColors.Card, BeamColors.Ink2))
    }
    Box(
        modifier
            .graphicsLayer {
                val s = if (pressed && enabled) 0.965f else 1f
                scaleX = s
                scaleY = s
            }
            .alpha(if (enabled || busy) 1f else 0.45f)
            .metalRing(
                shape = shape,
                ringWidth = if (lit) 2.0.dp else 1.5.dp,
                fill = BeamColors.Card,
                innerBrush = sheen,
                active = lit,
                halo = if (lit) 1.4f else 0.7f,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !busy,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.padding(contentPadding), contentAlignment = Alignment.Center) {
            when {
                busy -> BeamLoader(
                    size = 18.dp,
                    color = BeamColors.Chrome,
                )
                content != null -> content()
                label != null -> Text(
                    label,
                    color = BeamColors.Mist,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.2).sp,
                )
            }
        }
    }
}

/** Circular chrome-rimmed icon button — header controls, voice controls. */
@Composable
fun MetalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    fill: Color = BeamColors.Ink2,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lit = pressed || active
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val s = if (pressed && enabled) 0.88f else 1f
                scaleX = s
                scaleY = s
            }
            .alpha(if (enabled) 1f else 0.42f)
            .metalRing(
                shape = CircleShape,
                ringWidth = if (lit) 1.9.dp else 1.3.dp,
                fill = fill,
                active = lit,
                halo = if (lit) 1.5f else 0.55f,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Pill action. [emphasized] gets the solid chrome fill — used sparingly, so
 * the one primary action on a screen still wins against the dark rims.
 */
@Composable
fun ChromePill(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = Modifier.graphicsLayer {
        val s = if (pressed && enabled) 0.96f else 1f
        scaleX = s
        scaleY = s
    }
    val click = Modifier
        .alpha(if (enabled) 1f else 0.45f)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
    val pad = Modifier.padding(contentPadding)
    if (emphasized) {
        Box(
            modifier
                .then(scale)
                .metalRing(
                    shape = CircleShape,
                    ringWidth = 1.4.dp,
                    fill = BeamColors.Accent,
                    active = pressed,
                    halo = if (pressed) 1.6f else 0.9f,
                )
                .then(click)
                .then(pad),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = BeamColors.AccentInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        Box(
            modifier
                .then(scale)
                .metalEdge(CircleShape)
                .then(click)
                .then(pad),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = BeamColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Text masked by the chrome ramp — the wordmark treatment. The glyph is
 *  drawn white into an offscreen layer and the ramp composited over it with
 *  SrcIn, so only the letterforms pick up the metal. */
@Composable
fun ChromeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontSize: TextUnit = 24.sp,
) {
    val key = BeamColors.current.key
    // Vertical, not the ring sweep: chromeRamp's deep stops sit at 0/0.5/1 of
    // the angle, which lands on the horizontal axis of a wide, short text
    // block and paints headings near-black on the black background.
    val ramp = remember(key) {
        listOf(
            0.00f to BeamColors.Chrome,
            0.42f to BeamColors.Accent,
            0.50f to BeamColors.Fog,
            0.58f to BeamColors.Accent,
            1.00f to BeamColors.Chrome,
        )
    }
    Text(
        text,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(*ramp.toTypedArray()),
                    blendMode = BlendMode.SrcIn,
                )
            },
        style = style.copy(
            fontWeight = fontWeight,
            fontSize = fontSize.value.sp,
            letterSpacing = (-0.4).sp,
        ),
        color = Color.White,
    )
}

/** Flat swatch of a finish's own ramp — the theme picker shows real metal
 *  rather than four unrelated dots. */
@Composable
fun ChromeSwatch(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(5.dp)) {
    val key = BeamColors.current.key
    val ramp = remember(key) { chromeRamp().map { it.second } }
    Box(
        modifier.drawWithCache {
            val p = Path().apply {
                addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
            }
            val brush = Brush.sweepGradient(ramp, center = Offset(size.width / 2f, size.height / 2f))
            onDrawBehind { drawPath(p, brush) }
        },
    )
}
