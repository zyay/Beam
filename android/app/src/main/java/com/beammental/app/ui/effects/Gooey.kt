package com.beammental.app.ui.effects

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.annotation.RequiresApi
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.abs
import kotlin.math.min

/**
 * Liquid gooey — Beam's port of libraries.dev `liquid-gooey` 0.2.1.
 *
 * The library's central insight is that blur + alpha-contrast must never run
 * over real UI: filtering the actual content softens text, smears images and
 * eats shadows. So it splits the effect — a silhouette layer carries the goo
 * and the shadow, live DOM rides crisp on top. [GooeyLayer] is that split: the
 * merged liquid is drawn into an offscreen layer and filtered, the caller's
 * content is composed above it untouched.
 *
 * Below API 31 there is no chained `RenderEffect`, so the silhouette degrades
 * to plain shapes: they still slide and overlap, they just stop bridging.
 */

/** One piece of the liquid, positioned by its centre. */
data class GooeyBlob(
    val x: Dp,
    val y: Dp,
    val w: Dp,
    val h: Dp,
    val corner: Dp = 999.dp,
)

/**
 * A gooey silhouette with crisp content on top.
 *
 * Give the container room around the blobs — the blur spreads past their edges
 * by roughly [blur], and the layer does not clip it.
 *
 * @param blobs provider, called during the draw phase. Deferred rather than a
 *   plain list on purpose: a liquid built on [rememberLiquidMove] reads snapshot
 *   state that changes every frame, and resolving it here invalidates paint
 *   only instead of recomposing the caller's whole subtree per frame.
 * @param contrast alpha-contrast of the goo filter. The library default (18,
 *   with a -7 offset) is what makes two touching circles bridge instead of
 *   merely overlapping; lower it and the liquid thins out.
 */
@Composable
fun GooeyLayer(
    blobs: () -> List<GooeyBlob>,
    modifier: Modifier = Modifier,
    fill: Color = BeamColors.Card,
    blur: Dp = 6.dp,
    contrast: Float = 18f,
    content: @Composable BoxScope.() -> Unit,
) {
    val effect = rememberGooeyEffect(blur, contrast)
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    if (effect != null) renderEffect = effect
                }
                .drawBehind {
                    for (b in blobs()) {
                        val w = b.w.toPx()
                        val h = b.h.toPx()
                        val r = min(b.corner.toPx(), min(w, h) / 2f)
                        drawRoundRect(
                            color = fill,
                            topLeft = Offset(b.x.toPx() - w / 2f, b.y.toPx() - h / 2f),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(r, r),
                        )
                    }
                },
        )
        content()
    }
}

@Composable
private fun rememberGooeyEffect(blur: Dp, contrast: Float): RenderEffect? {
    if (Build.VERSION.SDK_INT < 31) return null
    val density = LocalDensity.current
    val radius = with(density) { blur.toPx() }
    return remember(radius, contrast) { gooeyRenderEffect(radius, contrast) }
}

@RequiresApi(31)
private fun gooeyRenderEffect(radius: Float, contrast: Float): RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(
        radius, radius, android.graphics.Shader.TileMode.DECAL,
    )
    // the SVG gooey matrix: identity on RGB, `contrast * a - offset` on alpha
    val matrix = android.graphics.ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, contrast, -(contrast * 0.5f - 2f),
        ),
    )
    val threshold = android.graphics.RenderEffect.createColorFilterEffect(
        android.graphics.ColorMatrixColorFilter(matrix),
    )
    return android.graphics.RenderEffect.createChainEffect(threshold, blur).asComposeRenderEffect()
}

/**
 * The "move" effect: a value that chases [target] on a spring, reporting how
 * far it stretched and how big its trailing droplet should be.
 *
 * Knobs are the library's, normalised 0..1: [springiness] 0 = heavy syrup,
 * 1 = near-instant; [wobble] is overshoot on arrival; [stretch] is velocity
 * stretch of the drop; [trail] is the trailing droplet's size, 0 disables it.
 * At their defaults the motion reproduces the tuned look.
 *
 * Read [position], [stretchAlong] and [trail] from a draw phase, not from
 * composition: they are snapshot state, so a moving liquid then invalidates
 * paint only and never recomposes the screen around it.
 */
class LiquidMotion internal constructor(
    private val anim: Animatable<Float, *>,
    private val stretchKnob: Float,
    private val trailKnob: Float,
) {
    val position: Float get() = anim.value

    /** How fast the liquid is travelling, signed. */
    val velocity: Float get() = anim.velocity

    /** 1 = round, >1 = pulled along the axis of travel. */
    val stretchAlong: Float
        get() = 1f + stretchKnob * min(1f, abs(anim.velocity) / VELOCITY_FULL)

    /** 1 = squashed across the axis of travel, conserving area. */
    val stretchAcross: Float get() = 1f / stretchAlong

    /** Trailing droplet size, 0 when settled or when `trail = 0`. */
    val trail: Float get() = trailKnob * min(1f, abs(anim.velocity) / VELOCITY_FULL)

    /** Which way the droplet tail points: -1 or 1 along the axis. */
    val direction: Float get() = if (anim.velocity >= 0f) 1f else -1f

    private companion object {
        /** Velocity at which stretch and trail saturate (dp-ish units/s). */
        const val VELOCITY_FULL = 1800f
    }
}

@Composable
fun rememberLiquidMove(
    target: Float,
    springiness: Float = 0.5f,
    wobble: Float = 0.5f,
    stretch: Float = 0.36f,
    trail: Float = 0.575f,
): LiquidMotion {
    val anim = remember { Animatable(target) }
    LaunchedEffect(target, springiness, wobble) {
        // springs compile to the same overshoot-and-settle the web build bakes
        // into a CSS linear() easing; damping 1 is the calm, critically damped
        // end of the wobble knob
        anim.animateTo(
            targetValue = target,
            animationSpec = spring(
                dampingRatio = 1f - 0.55f * wobble.coerceIn(0f, 1f),
                stiffness = 140f + 1500f * springiness.coerceIn(0f, 1f),
                visibilityThreshold = 0.001f,
            ),
        )
    }
    return remember(anim, stretch, trail) { LiquidMotion(anim, stretch, trail) }
}
