package com.beammental.app.ui.effects

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated mesh-gradient background. On API 33+ this is a true GPU shader
 * (RuntimeShader / AGSL): four soft colour blobs drift on Lissajous paths
 * and are mixed by exponential falloff. On API 24-32 it falls back to a
 * Canvas implementation that does the same math on the CPU.
 *
 * The result reads like the gradients on Vercel / Linear / Apple: organic
 * colour flow, no banding, no harsh edges, theme-tinted.
 *
 * [intensity] 0..1 — typically driven by `busy || streaming` so the
 * background subtly comes alive while a message is being produced.
 */
@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 0f,
) {
    val transition = rememberInfiniteTransition(label = "mesh-bg")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    val theme = BeamColors.current
    val live = 0.4f + 0.6f * intensity.coerceIn(0f, 1f)

    if (Build.VERSION.SDK_INT >= 33) {
        MeshShaderBackground(modifier = modifier, t = t, live = live, theme = theme)
    } else {
        MeshCanvasBackground(modifier = modifier, t = t, live = live, theme = theme)
    }
}

// ---------- API 33+ : AGSL shader ----------

private val MESH_AGSL = """
    uniform float2 iResolution;
    uniform float  iTime;
    uniform float  iLive;
    uniform float4 c1;
    uniform float2 p1;
    uniform float  r1;
    uniform float4 c2;
    uniform float2 p2;
    uniform float  r2;
    uniform float4 c3;
    uniform float2 p3;
    uniform float  r3;
    uniform float4 c4;
    uniform float2 p4;
    uniform float  r4;
    uniform float4 cInk;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution;
        float3 sum = float3(0.0);
        float  wsum = 0.0;
        float d1 = length(uv - p1);
        float w1 = exp(-d1 * d1 / max(r1 * r1, 0.0001));
        sum  += c1.rgb * w1;
        wsum += w1;
        float d2 = length(uv - p2);
        float w2 = exp(-d2 * d2 / max(r2 * r2, 0.0001));
        sum  += c2.rgb * w2;
        wsum += w2;
        float d3 = length(uv - p3);
        float w3 = exp(-d3 * d3 / max(r3 * r3, 0.0001));
        sum  += c3.rgb * w3;
        wsum += w3;
        float d4 = length(uv - p4);
        float w4 = exp(-d4 * d4 / max(r4 * r4, 0.0001));
        sum  += c4.rgb * w4;
        wsum += w4;
        float3 c = sum / max(wsum, 0.0001);
        // tint by the active theme ink so the background never washes out
        c = mix(cInk.rgb, c, 0.85 + 0.15 * iLive);
        return half4(c, 1.0);
    }
""".trimIndent()

@Composable
private fun MeshShaderBackground(
    modifier: Modifier,
    t: Float,
    live: Float,
    theme: com.beammental.app.ui.theme.BeamThemeDef,
) {
    val shader = remember { RuntimeShader(MESH_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    val time = t * 2f * PI.toFloat()
    val w = theme.wave
    // four blobs drifting on Lissajous paths, weighted by intensity
    val drift = 0.30f + 0.10f * live
    Canvas(modifier) {
        val pw = size.width
        val ph = size.height
        if (pw <= 0f || ph <= 0f) return@Canvas
        shader.setFloatUniform("iResolution", pw, ph)
        shader.setFloatUniform("iTime", time)
        shader.setFloatUniform("iLive", live)
        shader.setFloatUniform("c1", w[0].red, w[0].green, w[0].blue, 1f)
        shader.setFloatUniform(
            "p1",
            0.30f + drift * (0.5f + 0.5f * sin(time * 0.55f)),
            0.25f + drift * (0.5f + 0.5f * cos(time * 0.41f)),
        )
        shader.setFloatUniform("r1", 0.45f)
        shader.setFloatUniform("c2", w[1].red, w[1].green, w[1].blue, 1f)
        shader.setFloatUniform(
            "p2",
            0.70f + drift * (0.5f + 0.5f * sin(time * 0.37f + 1.3f)),
            0.30f + drift * (0.5f + 0.5f * cos(time * 0.49f + 0.7f)),
        )
        shader.setFloatUniform("r2", 0.50f)
        shader.setFloatUniform("c3", w[2].red, w[2].green, w[2].blue, 1f)
        shader.setFloatUniform(
            "p3",
            0.25f + drift * (0.5f + 0.5f * sin(time * 0.31f + 2.1f)),
            0.75f + drift * (0.5f + 0.5f * cos(time * 0.43f + 1.4f)),
        )
        shader.setFloatUniform("r3", 0.55f)
        shader.setFloatUniform("c4", w[3 % w.size].red, w[3 % w.size].green, w[3 % w.size].blue, 1f)
        shader.setFloatUniform(
            "p4",
            0.75f + drift * (0.5f + 0.5f * sin(time * 0.29f + 3.4f)),
            0.72f + drift * (0.5f + 0.5f * cos(time * 0.45f + 2.2f)),
        )
        shader.setFloatUniform("r4", 0.50f)
        shader.setFloatUniform("cInk", theme.ink.red, theme.ink.green, theme.ink.blue, 1f)
        drawRect(brush = brush)
    }
}

// ---------- API 24-32 : Canvas fallback (CPU math) ----------

@Composable
private fun MeshCanvasBackground(
    modifier: Modifier,
    t: Float,
    live: Float,
    theme: com.beammental.app.ui.theme.BeamThemeDef,
) {
    val time = t * 2f * PI.toFloat()
    val w = theme.wave
    val drift = 0.30f + 0.10f * live
    Canvas(modifier) {
        val pw = size.width
        val ph = size.height
        if (pw <= 0f || ph <= 0f) return@Canvas
        // 4 drifting radial blobs — visually similar to the shader, just
        // rendered as additive radial gradients on the CPU.
        val blobs = listOf(
            Blob(
                color = w[0],
                cx = pw * (0.30f + drift * (0.5f + 0.5f * sin(time * 0.55f))),
                cy = ph * (0.25f + drift * (0.5f + 0.5f * cos(time * 0.41f))),
                r = pw * 0.55f,
            ),
            Blob(
                color = w[1],
                cx = pw * (0.70f + drift * (0.5f + 0.5f * sin(time * 0.37f + 1.3f))),
                cy = ph * (0.30f + drift * (0.5f + 0.5f * cos(time * 0.49f + 0.7f))),
                r = pw * 0.60f,
            ),
            Blob(
                color = w[2],
                cx = pw * (0.25f + drift * (0.5f + 0.5f * sin(time * 0.31f + 2.1f))),
                cy = ph * (0.75f + drift * (0.5f + 0.5f * cos(time * 0.43f + 1.4f))),
                r = pw * 0.65f,
            ),
            Blob(
                color = w[3 % w.size],
                cx = pw * (0.75f + drift * (0.5f + 0.5f * sin(time * 0.29f + 3.4f))),
                cy = ph * (0.72f + drift * (0.5f + 0.5f * cos(time * 0.45f + 2.2f))),
                r = pw * 0.60f,
            ),
        )
        // Base ink wash so the blobs read on dark themes.
        drawRect(color = theme.ink)
        blobs.forEach { b ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        b.color.copy(alpha = 0.45f + 0.20f * live),
                        Color.Transparent,
                    ),
                    center = Offset(b.cx, b.cy),
                    radius = b.r,
                ),
                radius = b.r,
                center = Offset(b.cx, b.cy),
            )
        }
        // subtle vignette to keep the eye centered
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, theme.ink.copy(alpha = 0.55f)),
                center = Offset(pw * 0.5f, ph * 0.5f),
                radius = maxOf(pw, ph) * 0.78f,
            ),
        )
        val gridStep = 36.dp.toPx()
        drawHairlineGrid(this, step = gridStep, theme = theme)
    }
}

private data class Blob(val color: Color, val cx: Float, val cy: Float, val r: Float)

/** Faint grid lines, very low alpha — gives the mesh gradient depth. */
private fun drawHairlineGrid(scope: DrawScope, step: Float, theme: com.beammental.app.ui.theme.BeamThemeDef) {
    val s = scope.size
    val a = 0.04f
    val c = theme.mist.copy(alpha = a)
    var x = 0f
    while (x <= s.width) {
        scope.drawLine(c, start = Offset(x, 0f), end = Offset(x, s.height), strokeWidth = 0.6f)
        x += step
    }
    var y = 0f
    while (y <= s.height) {
        scope.drawLine(c, start = Offset(0f, y), end = Offset(s.width, y), strokeWidth = 0.6f)
        y += step
    }
}
