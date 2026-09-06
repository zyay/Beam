package com.beammental.app.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Static hairline blueprint grid over the warm dark background — auth texture. */
@Composable
fun BlueprintGrid(modifier: Modifier = Modifier, cell: Dp = 32.dp) {
    Canvas(modifier) {
        val step = cell.toPx()
        val faint = Color(0xFFEDE8DC).copy(alpha = 0.035f)
        val strong = Color(0xFFEDE8DC).copy(alpha = 0.07f)
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
