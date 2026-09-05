package com.beammental.app.ui.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Drifting light beams over near-black — the signature auth background.
 * Compose port of the OGL shader used on the web.
 */
@Composable
fun BeamBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "beams")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )

    Canvas(modifier.fillMaxSize()) {
        drawRect(BeamColors.Ink)
        beam(this, t * 0.9f, 0.62f, 0.06f, BeamColors.Pink, 0.38f)
        beam(this, t * 0.6f + 2.1f, 0.40f, 0.035f, BeamColors.Violet, 0.42f)
        beam(this, t * 0.7f + 4.2f, 0.78f, 0.025f, BeamColors.Mint, 0.30f)
    }
}

private fun beam(
    scope: DrawScope,
    phase: Float,
    centerFrac: Float,
    widthFrac: Float,
    color: Color,
    alpha: Float,
) {
    val w = scope.size.width
    val h = scope.size.height
    val diag = sqrt(w * w + h * h)
    val angle = 0.5f
    val center = centerFrac * diag + sin(phase) * diag * 0.14f

    scope.drawIntoCanvas { canvas ->
        val paint = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            this.alpha = (alpha * 255).toInt()
            shader = android.graphics.LinearGradient(
                0f, center, w, center - w * tan(angle),
                intArrayOf(
                    color.copy(alpha = 0f).toArgb(),
                    color.copy(alpha = 1f).toArgb(),
                    color.copy(alpha = 0f).toArgb(),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                android.graphics.Shader.TileMode.CLAMP,
            )
            maskFilter = android.graphics.BlurMaskFilter(
                widthFrac * diag * 0.55f,
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        }
        val half = widthFrac * diag
        val path = Path().apply {
            moveTo(-w * 0.2f, center - half)
            lineTo(w * 1.2f, center - w * tan(angle) - half)
            lineTo(w * 1.2f, center - w * tan(angle) + half)
            lineTo(-w * 0.2f, center + half)
            close()
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

/** Particle sphere — the "thinking" indicator (port of ThinkingOrb.tsx). */
@Composable
fun ThinkingOrb(modifier: Modifier = Modifier, orbSize: Float = 44f) {
    val transition = rememberInfiniteTransition(label = "orb")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbT",
    )

    Canvas(modifier.fillMaxSize()) {
        val d = density
        val n = 140
        val golden = (Math.PI * (3.0 - sqrt(5.0))).toFloat()
        val r = orbSize * 0.36f * d
        val cx = size.width / 2f
        val cy = size.height / 2f
        val cosA = cos(t); val sinA = sin(t)
        val cosB = cos(t * 0.7f); val sinB = sin(t * 0.7f)

        for (i in 0 until n) {
            val y = 1f - 2f * i / (n - 1)
            val rad = sqrt((1f - y * y).coerceAtLeast(0f))
            val a = golden * i
            val px = cos(a) * rad
            val pz = sin(a) * rad

            val x1 = px * cosA + pz * sinA
            val z1 = -px * sinA + pz * cosA
            val y2 = y * cosB - z1 * sinB
            val z2 = y * sinB + z1 * cosB

            val persp = 1f / (1f + z2 * 0.35f)
            val sx = cx + x1 * r * persp
            val sy = cy + y2 * r * persp
            val depth = (z2 + 1f) / 2f

            drawCircle(
                color = Color(0xFFC4B5FD).copy(alpha = 0.18f + depth * 0.72f),
                radius = (0.6f + depth * 1.15f) * d,
                center = Offset(sx, sy),
            )
        }
    }
}

/** Animated beam running along a rounded border (modifier port of BorderBeam). */
@Composable
fun Modifier.beamBorder(): Modifier {
    val transition = rememberInfiniteTransition(label = "beamBorder")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Restart),
        label = "beamAngle",
    )
    val borderColors = listOf(
        Color.Transparent, Color.Transparent,
        Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.5f),
        Color.White.copy(alpha = 0.25f),
        Color.Transparent,
    )
    return this.drawBehind {
        val d = density
        val brush = Brush.sweepGradient(borderColors, center = center)
        rotate(angle, pivot = center) {
            drawRoundRect(
                brush = brush,
                cornerRadius = CornerRadius(20f * d, 20f * d),
                style = Stroke(width = 1.5f * d),
            )
        }
    }
}

/**
 * The Beam mascot, drawn natively (Grok Bot style): a little white blob
 * with two oval eyes. Bobbing idle; while "thinking" the eyes glance around.
 */
@Composable
fun MascotBlob(modifier: Modifier = Modifier, blobSize: Dp = 40.dp, thinking: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "mascot")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val glance by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "glance",
    )

    Canvas(modifier) {
        val d = blobSize.toPx()
        val bobPx = bob * d * 0.05f
        val cx = size.width / 2f
        val cy = size.height / 2f + bobPx

        // body: rounded squircle
        val bodyW = d * 0.88f
        val bodyH = d * 0.84f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(cx - bodyW / 2f, cy - bodyH / 2f),
            size = androidx.compose.ui.geometry.Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyW * 0.38f, bodyW * 0.38f),
        )

        // eyes: two vertical dark ovals
        val eyeW = d * 0.10f
        val eyeH = d * 0.24f
        val eyeY = cy - d * 0.06f
        val gap = d * 0.16f + if (thinking) glance * d * 0.025f else 0f
        val eyeColor = Color(0xFF0A0A0C)
        drawRoundRect(
            color = eyeColor,
            topLeft = Offset(cx - gap - eyeW / 2f, eyeY - eyeH / 2f),
            size = androidx.compose.ui.geometry.Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2f, eyeW / 2f),
        )
        drawRoundRect(
            color = eyeColor,
            topLeft = Offset(cx + gap - eyeW / 2f, eyeY - eyeH / 2f),
            size = androidx.compose.ui.geometry.Size(eyeW, eyeH),
            cornerRadius = CornerRadius(eyeW / 2f, eyeW / 2f),
        )
    }
}
