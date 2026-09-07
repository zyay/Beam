package com.beammental.app.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private fun hash01(n: Float): Float {
    val v = sin(n * 127.1f + 311.7f) * 43758.5453f
    return v - floor(v)
}

/** Chat screen background: deep wash + three drifting aurora orbs that
 *  breathe in/out, a slow Lissajous motion, faint grid dots, soft vignette.
 *  [intensity] 0..1 — typically driven by `busy || streaming` so the room
 *  subtly comes alive while a message is being composed or answered. */
@Composable
fun ChatBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 0f,
) {
    val transition = rememberInfiniteTransition(label = "chat-bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val theme = BeamColors.current
    val wave = theme.wave

    // smoothed intensity: don't flicker between states
    val smooth = remember { Animatable(0f) }
    LaunchedEffect(intensity) {
        smooth.animateTo(intensity.coerceIn(0f, 1f), tween(900))
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val time = t * 2f * PI.toFloat()
        val live = 0.4f + 0.6f * smooth.value

        // 1 · deep wash
        drawRect(
            brush = Brush.verticalGradient(
                0f to wave[0].copy(alpha = 0.07f + 0.05f * live),
                0.45f to Color.Transparent,
                1f to theme.ink.copy(alpha = 0.6f),
            ),
        )

        // 2 · three drifting aurora orbs
        for (i in 0..2) {
            val ax = 0.22f + 0.56f * (0.5f + 0.5f * sin(time * (0.55f + i * 0.19f) + i * 2.4f))
            val ay = 0.18f + 0.22f * i + 0.06f * cos(time * (0.85f + i * 0.27f) + i)
            val r = h * (0.34f + 0.04f * sin(time * 1.05f + i * 1.7f))
            val b = 0.5f + 0.5f * sin(breath * 2f * PI.toFloat() + i * 1.3f)
            val c = wave[(i + 1) % wave.size]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        c.copy(alpha = (0.06f + 0.05f * b) * (0.7f + 0.6f * live)),
                        Color.Transparent,
                    ),
                    center = Offset(w * ax, h * ay),
                    radius = r,
                ),
                radius = r,
                center = Offset(w * ax, h * ay),
            )
        }

        // 3 · thinking ripples — only when a message is being produced
        if (smooth.value > 0.05f) {
            val cx = w * 0.5f
            val cy = h * (0.66f + 0.04f * sin(time * 0.7f))
            for (k in 0 until 3) {
                val progress = (breath + k / 3f) % 1f
                val rr = (h * 0.18f) * progress + h * 0.05f
                val a = (1f - progress) * (0.10f + 0.06f * smooth.value)
                drawCircle(
                    color = wave[0].copy(alpha = a),
                    radius = rr,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f.dp.toPx()),
                )
            }
        }

        // 4 · grid dots — quiet texture
        val step = 26.dp.toPx()
        val baseAlpha = 0.04f + 0.02f * live
        var gy = step * 0.5f
        var row = 0
        while (gy < h) {
            var gx = step * 0.5f
            var col = 0
            while (gx < w) {
                val jitter = hash01(row * 37.3f + col * 11.7f)
                val a = baseAlpha * (0.4f + 0.6f * jitter)
                drawCircle(
                    color = wave[0].copy(alpha = a),
                    radius = 0.9f.dp.toPx() * (0.6f + 0.6f * jitter),
                    center = Offset(gx, gy),
                )
                gx += step
                col++
            }
            gy += step
            row++
        }

        // 5 · vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, theme.ink.copy(alpha = 0.55f)),
                center = Offset(w * 0.5f, h * 0.45f),
                radius = max(w, h) * 0.78f,
            ),
        )
    }
}
