package com.beammental.app.ui.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private fun hash01(n: Float): Float {
    val v = sin(n * 127.1f + 311.7f) * 43758.5453f
    return v - floor(v)
}

/** Layered ambient background: a deep tint wash, three slow aurora blooms
 *  drifting on Lissajous paths, four translucent waves whose ridges carry a
 *  faint glowing line, twinkling particles, and a soft vignette for depth.
 *  Everything breathes on one 36 s loop, in the active theme's colors. */
@Composable
fun WaveBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(36000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "drift",
    )
    val theme = BeamColors.current
    val wave = theme.wave

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val time = t * 2f * PI.toFloat()

        // 1 · deep wash — color bleeding in from the top, depth at the bottom
        drawRect(
            brush = Brush.verticalGradient(
                0f to wave[0].copy(alpha = 0.055f),
                0.42f to Color.Transparent,
                1f to theme.ink.copy(alpha = 0.55f),
            ),
        )

        // 2 · aurora blooms drifting on Lissajous paths
        for (i in 0..2) {
            val cx = w * (0.22f + 0.56f * (0.5f + 0.5f * sin(time * (0.6f + i * 0.23f) + i * 2.4f)))
            val cy = h * (0.14f + 0.26f * i + 0.05f * sin(time * (0.9f + i * 0.31f) + i))
            val r = h * (0.30f + 0.05f * sin(time * 1.1f + i * 1.7f))
            val breathe = 0.5f + 0.5f * sin(time * 0.8f + i * 2f)
            val c = wave[(i + 1) % wave.size]
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(c.copy(alpha = 0.045f + 0.04f * breathe), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }

        // 3 · waves — translucent bodies with a glowing ridge line
        for (i in 0 until 4) {
            val c = wave[i % wave.size]
            val dir = if (i % 2 == 0) 1f else -1f
            val baseY = h * (0.38f + i * 0.15f)
            val amp = h * (0.035f + (i % 2) * 0.018f)
            val phase = time * (0.8f + i * 0.26f) * dir + i * 1.9f

            val ridge = Path()
            var x = 0f
            while (x <= w) {
                val u = x / w
                val y = baseY +
                    sin(u * 3.6f * PI.toFloat() + phase) * amp +
                    sin(u * 7.7f * PI.toFloat() - phase * 1.4f) * amp * 0.35f +
                    sin(u * 1.3f * PI.toFloat() + phase * 0.6f) * amp * 0.55f
                if (x == 0f) ridge.moveTo(x, y) else ridge.lineTo(x, y)
                x += 12f
            }

            val body = Path().apply {
                addPath(ridge)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = body,
                brush = Brush.verticalGradient(
                    0f to c.copy(alpha = 0.05f + i * 0.014f),
                    1f to c.copy(alpha = 0f),
                    startY = (baseY - amp * 2f).coerceAtLeast(0f),
                    endY = h,
                ),
            )
            // glowing crest — brightest at the middle, fading to the edges
            drawPath(
                path = ridge,
                brush = Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        c.copy(alpha = 0.16f + 0.10f * sin(time + i)),
                        Color.Transparent,
                    ),
                ),
                style = Stroke(width = 1.6.dp.toPx()),
            )
        }

        // 4 · particles — soft halo with a bright core, twinkling as they rise
        for (i in 0 until 18) {
            val speed = 0.30f + hash01(i * 7.7f) * 0.45f
            val cycle = (t * speed + hash01(i * 5.3f)) % 1f
            val y = (1f - cycle) * h
            val x = hash01(i * 3.1f) * w + sin(time * 1.2f + i * 2.1f) * 16f
            val fade = sin(PI.toFloat() * cycle).coerceIn(0f, 1f)
            val twinkle = 0.55f + 0.45f * sin(time * (1.4f + hash01(i * 9.1f)) + i * 3.7f)
            val core = 1f + hash01(i * 11.3f) * 1.8f
            val c = if (i % 3 == 0) wave[i % wave.size] else BeamColors.Mist
            val a = (0.10f + 0.16f * hash01(i * 13.9f)) * fade * twinkle
            drawCircle(color = c.copy(alpha = a * 0.22f), radius = core * 3.2f, center = Offset(x, y))
            drawCircle(color = c.copy(alpha = a), radius = core, center = Offset(x, y))
        }

        // 5 · vignette — pulls the eye toward the content
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, theme.ink.copy(alpha = 0.5f)),
                center = Offset(w * 0.5f, h * 0.45f),
                radius = max(w, h) * 0.78f,
            ),
        )
    }
}
