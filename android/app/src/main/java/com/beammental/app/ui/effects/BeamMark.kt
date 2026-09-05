package com.beammental.app.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import com.beammental.app.ui.theme.BeamColors
import androidx.compose.ui.unit.dp

/** The Beam logo: a gradient arc rising to a dot. */
@Composable
fun BeamMark(size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val brush = Brush.linearGradient(
            listOf(BeamColors.Pink, BeamColors.Violet, BeamColors.Mint),
            start = Offset(this.size.width * 0.1f, this.size.height * 0.85f),
            end = Offset(this.size.width * 0.9f, this.size.height * 0.15f),
        )
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.1f
        val arc = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.16f, h * 0.76f)
            cubicTo(w * 0.40f, h * 0.76f, w * 0.44f, h * 0.26f, w * 0.64f, h * 0.26f)
            cubicTo(w * 0.78f, h * 0.26f, w * 0.85f, h * 0.38f, w * 0.85f, h * 0.50f)
        }
        drawPath(arc, brush, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(brush, radius = w * 0.08f, center = Offset(w * 0.85f, h * 0.50f))
    }
}
