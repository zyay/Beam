package com.beammental.app.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BeamLoader(
    size: Dp = 20.dp,
    color: Color = BeamColors.Accent,
) {
    val t = EffectClock.t
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val orbitR = cx * 0.55f
            val dotR = cx * 0.18f

            if (EffectClock.reducedMotion) {
                val pulse = 0.5f + 0.5f * sin(t * 3.0).toFloat()
                drawCircle(
                    color = color.copy(alpha = 0.4f + 0.6f * pulse),
                    radius = cx * 0.3f,
                    center = Offset(cx, cy),
                )
                return@Canvas
            }

            val angle = (t * 2.8).toFloat()
            val haloAlpha = (0.08f + 0.06f * sin(t * 4.0).toFloat()).coerceIn(0f, 1f)

            drawHalo(cx, cy, cx * 0.85f, color, haloAlpha)

            val dx = cx + orbitR * cos(angle)
            val dy = cy + orbitR * sin(angle)
            drawCircle(
                color = color.copy(alpha = 0.25f),
                radius = dotR * 2.2f,
                center = Offset(dx, dy),
                blendMode = BlendMode.Plus,
            )
            drawCircle(
                color = color,
                radius = dotR,
                center = Offset(dx, dy),
            )

            val trailAngle = angle - PI.toFloat() * 0.6f
            val tx = cx + orbitR * cos(trailAngle)
            val ty = cy + orbitR * sin(trailAngle)
            drawCircle(
                color = color.copy(alpha = 0.12f),
                radius = dotR * 1.4f,
                center = Offset(tx, ty),
                blendMode = BlendMode.Plus,
            )
        }
    }
}

private fun DrawScope.drawHalo(cx: Float, cy: Float, r: Float, color: Color, alpha: Float) {
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = r,
        center = Offset(cx, cy),
        blendMode = BlendMode.Plus,
    )
}
