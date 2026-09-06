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
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

private fun hash01(n: Float): Float {
    val v = sin(n * 127.1f + 311.7f) * 43758.5453f
    return v - floor(v)
}

/** The icon's flowing wave gradient, brought to life: a breathing glow,
 *  six slow-drifting translucent waves (each its own direction and harmonics)
 *  and soft particles rising through them — all in the active theme colors. */
@Composable
fun WaveBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waves")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(30000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "drift",
    )
    val wave = BeamColors.current.wave

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val time = t * 2f * PI.toFloat()

        // breathing glow behind everything, like light filtering through water
        val breath = 0.5f + 0.5f * sin(time * 0.7f)
        val glowCenter = Offset(w * 0.5f, h * 0.20f)
        val glowRadius = h * 0.75f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    wave[0].copy(alpha = 0.09f + 0.06f * breath),
                    Color.Transparent,
                ),
                center = glowCenter,
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = glowCenter,
        )

        // six waves: alternate drift direction, three harmonic mixes
        for (i in 0 until 6) {
            val c = wave[i % wave.size]
            val dir = if (i % 2 == 0) 1f else -1f
            val baseY = h * (0.26f + i * 0.13f)
            val amp = h * (0.045f + (i % 3) * 0.016f)
            val phase = time * (1f + i * 0.22f) * dir + i * 1.7f
            val alpha = 0.035f + i * 0.011f

            val path = Path()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w) {
                val u = x / w
                val y = baseY +
                    sin(u * 4.4f * PI.toFloat() + phase) * amp +
                    sin(u * 9.1f * PI.toFloat() - phase * 1.6f) * amp * 0.4f +
                    sin(u * 1.6f * PI.toFloat() + phase * 0.5f) * amp * 0.65f
                path.lineTo(x, y)
                x += 10f
            }
            path.lineTo(w, h)
            path.close()

            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    0f to c.copy(alpha = alpha),
                    1f to c.copy(alpha = 0f),
                    startY = (baseY - amp).coerceAtLeast(0f),
                    endY = h,
                ),
            )
        }

        // particles rising through the water, gently swaying
        for (i in 0 until 14) {
            val speed = 0.35f + hash01(i * 7.7f) * 0.45f
            val cycle = (t * speed + hash01(i * 5.3f)) % 1f
            val y = (1f - cycle) * h
            val x = hash01(i * 3.1f) * w + sin(time * 1.3f + i * 2.1f) * 14f
            val radius = 1.2f + hash01(i * 11.3f) * 2.4f
            val fade = sin(PI.toFloat() * cycle).coerceIn(0f, 1f) // soft in/out over the rise
            val alpha = (0.03f + 0.05f * hash01(i * 13.9f)) * fade
            drawCircle(
                color = if (i % 3 == 0) wave[i % wave.size].copy(alpha = alpha) else BeamColors.Mist.copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y),
            )
        }
    }
}
