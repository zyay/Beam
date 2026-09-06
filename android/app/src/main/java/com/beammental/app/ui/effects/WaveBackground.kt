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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.PI
import kotlin.math.sin

/** The icon's flowing wave gradient, brought to life: four slow-drifting
 *  translucent waves in the active theme's colors over the dark base. */
@Composable
fun WaveBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "waves")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(26000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "drift",
    )
    val wave = BeamColors.current.wave

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        wave.forEachIndexed { i, c ->
            val baseY = h * (0.30f + i * 0.16f)
            val amp = h * (0.05f + (i % 2) * 0.022f)
            val phase = t * 2f * PI.toFloat() + i * 1.7f
            val alpha = 0.045f + i * 0.012f

            val path = Path()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w) {
                val y = baseY +
                    sin(x / w * 4.4f * PI.toFloat() + phase) * amp +
                    sin(x / w * 9.1f * PI.toFloat() - phase * 1.6f) * amp * 0.4f
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
    }
}
