package com.beammental.app.ui.effects

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Image generation mosaic — Beam's port of libraries.dev `img-fx` 0.5.1.
 *
 * The web effect is a WebGL shader: a cell mosaic that churns while something
 * is "generating", then materialises an image cell-by-cell along a travelling
 * diagonal band. Compose has no shader budget inside a chat list, so this draws
 * the same thing as cells — and keeps the three properties that make the
 * original read correctly:
 *
 *  - **scale invariance**: cell size is fixed in dp, so the cell *count* grows
 *    with the card instead of the cells turning chunky
 *  - **the 10 fps cap**: the flicker clock is quantised to 100 ms, which is
 *    what gives the churn a slow deliberate drift instead of TV static
 *  - **reveal in lockstep with the grid**: the image never fades in as a whole,
 *    it flips on cell by cell as the band passes
 */

enum class MosaicPreset(val cell: Dp, val gap: Float) {
    /** Chromium Flow: irregular, breathing cells. */
    PixelsOrganic(12.dp, 0.22f),

    /** Nebula: a strict grid with hard, high-contrast flicker. */
    PixelsMechanic(9.dp, 0.12f),

    /** Diagonal gradient sweep with per-cell flicker — the fast "generating"
     *  read, and the one that suits a text-heavy app. */
    SweepGradient(8.dp, 0.06f),
}

/** the shader's own clock cap: 10 fps */
private const val TICK_MS = 100L

private class BandCell(val rect: Rect, val under: Color, val alpha: Float)

/**
 * @param painter the image to materialise. `null` runs the churn forever —
 *   the loading state with nothing to reveal.
 * @param reveal 0..1 position of the travelling band across the diagonal.
 *   Animate it; 0 is all mosaic, 1 is all image.
 * @param strength final opacity multiplier, exactly as in the web props: it
 *   dims the whole effect without touching its animation.
 */
@Composable
fun MosaicReveal(
    modifier: Modifier = Modifier,
    painter: Painter? = null,
    reveal: Float = 0f,
    preset: MosaicPreset = MosaicPreset.SweepGradient,
    pixelScale: Float = 1f,
    strength: Float = 1f,
    paused: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    colors: List<Color> = BeamColors.BeamPalette,
) {
    val surface = BeamColors.Card
    val highlight = BeamColors.Mist
    Box(
        modifier
            .clip(shape)
            .drawWithCache {
                val cellPx = preset.cell.toPx() * pixelScale.coerceAtLeast(0.25f)
                val cols = (size.width / cellPx).toInt().coerceAtLeast(1)
                val rows = (size.height / cellPx).toInt().coerceAtLeast(1)
                val gapPx = cellPx * preset.gap
                val palette = colors
                val revealed = Path()
                val band = ArrayList<BandCell>(cols)
                val hairline = 1.dp.toPx()

                onDrawBehind {
                    if (strength <= 0f) return@onDrawBehind
                    val tick = if (paused) 0L else EffectClock.millis / TICK_MS
                    val t = tick * 0.1
                    val sweep = ((t * 0.18) % 1.0).toFloat()
                    val progress = reveal.coerceIn(0f, 1f)
                    // cells this close to the band cross-fade instead of
                    // flipping, so the reveal reads as a materialisation
                    // rather than a diagonal wipe
                    val soft = 0.14f

                    revealed.reset()
                    band.clear()

                    for (row in 0 until rows) {
                        for (col in 0 until cols) {
                            val p = (col.toFloat() / cols + row.toFloat() / rows) * 0.5f
                            val jitter = if (preset == MosaicPreset.PixelsOrganic) {
                                hash01(col * 1.7f, row * 2.3f) * 0.35f
                            } else 0f
                            val rect = Rect(
                                Offset(col * cellPx + gapPx * 0.5f + jitter * gapPx, row * cellPx + gapPx * 0.5f + jitter * gapPx),
                                Size(cellPx - gapPx, cellPx - gapPx),
                            )
                            val cell = cellColor(p, sweep, t, col, row, preset, palette, surface)
                            when {
                                painter == null -> drawCell(rect, cell, hairline, highlight, strength)
                                (p - progress) / soft <= -1f -> revealed.addRect(rect)
                                (p - progress) / soft < 1f ->
                                    band.add(BandCell(rect, cell, (1f - (p - progress) / soft) * 0.5f))
                                else -> drawCell(rect, cell, hairline, highlight, strength)
                            }
                        }
                    }

                    if (painter != null) {
                        if (!revealed.isEmpty) clipPath(revealed) { drawPainter(painter, strength) }
                        for (b in band) {
                            val a = b.alpha.coerceIn(0f, 1f)
                            drawCell(b.rect, b.under, hairline, highlight, strength * (1f - a))
                            clipRect(b.rect.left, b.rect.top, b.rect.right, b.rect.bottom) {
                                drawPainter(painter, strength * a)
                            }
                        }
                    }
                }
            },
    )
}

private fun DrawScope.drawPainter(painter: Painter, alpha: Float) {
    if (alpha <= 0f) return
    // the painter fills the whole scope; the caller's clip decides which cell
    // of it survives, so one intrinsic-size draw serves every revealed cell
    with(painter) {
        draw(
            size = this@drawPainter.size,
            alpha = alpha.coerceIn(0f, 1f),
        )
    }
}

private fun cellColor(
    p: Float,
    sweep: Float,
    t: Double,
    col: Int,
    row: Int,
    preset: MosaicPreset,
    palette: List<Color>,
    surface: Color,
): Color {
    val flick = hash01(col * 1.31f, row * 2.17f + t.toFloat() * 0.37f)
    val proximity = 1f - min(1f, abs(p - sweep) * 3.2f)
    val at = { i: Int -> palette[((i % palette.size) + palette.size) % palette.size] }
    return when (preset) {
        MosaicPreset.SweepGradient -> {
            // a diagonal gradient out of the card surface toward the palette,
            // broken up by per-cell flicker
            val g = lerp(surface, at((p * palette.size).toInt()), 0.30f + 0.42f * proximity)
            if (flick > 0.72f) g else lerp(surface, g, 0.35f)
        }
        MosaicPreset.PixelsMechanic -> {
            val c = at((hash01(col * 3.1f, row * 5.7f) * palette.size).toInt())
            if (flick > 0.55f - 0.35f * proximity) c else surface
        }
        MosaicPreset.PixelsOrganic -> {
            val c = at((hash01(col * 2.3f + 7f, row * 4.1f) * palette.size).toInt())
            val breath = 0.5f + 0.5f * sin(t * 0.9 + col * 0.4 + row * 0.31).toFloat()
            lerp(surface, c, (0.18f + 0.55f * proximity) * (0.45f + 0.55f * breath) + 0.12f * flick)
        }
    }
}

private fun DrawScope.drawCell(rect: Rect, color: Color, hairline: Float, highlight: Color, strength: Float) {
    if (strength <= 0f) return
    drawRect(color = color, topLeft = rect.topLeft, size = rect.size, alpha = strength.coerceIn(0f, 1f))
    // a hair of top-light on the brighter cells: the mosaic then reads as tiles
    // sitting proud of the surface rather than as flat noise
    if (color.luminance() > 0.35f) {
        drawRect(
            color = highlight.copy(alpha = 0.10f * strength),
            topLeft = rect.topLeft,
            size = Size(rect.width, hairline),
        )
    }
}

/** Deterministic hash in [0, 1) — the same lattice noise the orb engine uses. */
private fun hash01(a: Float, b: Float): Float {
    val h = sin((a * 12.9898f + b * 78.233f).toDouble()) * 43758.5453
    return (h - floor(h)).toFloat()
}
