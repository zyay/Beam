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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Static hairline blueprint grid over flat black — the auth background texture. */
@Composable
fun BlueprintGrid(modifier: Modifier = Modifier, cell: Dp = 32.dp) {
    Canvas(modifier) {
        val step = cell.toPx()
        val faint = Color.White.copy(alpha = 0.045f)
        val strong = Color.White.copy(alpha = 0.09f)
        var i = 0
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = if (i % 4 == 0) strong else faint,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
            x += step
            i++
        }
        var j = 0
        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = if (j % 4 == 0) strong else faint,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
            j++
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
