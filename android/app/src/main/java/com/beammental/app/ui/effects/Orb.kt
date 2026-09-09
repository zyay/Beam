package com.beammental.app.ui.effects

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Beam orb — light diffusing through a cloud.
 *
 * A direct port of the SHDR-21 volumetric shader (components/ui/shdr-21.tsx)
 * to AGSL. It is not a sprite and not a gradient: a ray is marched through a
 * density field bounded by a sphere, accumulating transmittance, a short
 * self-shadow march toward the light, and in-scattered light weighted by a
 * Henyey-Greenstein phase function. That is what makes it read as volume
 * rather than as a flat glow.
 *
 * Cost control: the march is 30 steps x 3 density evaluations, which is far
 * too much to run at view resolution on a phone, so it renders into a small
 * offscreen bitmap and is upscaled bilinearly. Clouds are low-frequency, so
 * the upscale is invisible — this is the same trick the web version gets for
 * free from canvas sizing.
 */

/** What the orb is doing. Drives palette, energy and how [level] is read. */
enum class OrbState { Idle, Listening, Thinking, Speaking }

private data class OrbLook(
    val light: Color,
    val shadow: Color,
    val power: Float,
    val ambient: Float,
    val shadowLift: Float,
    val density: Float,
    val speed: Float,
)

/*
 * The light colour is kept close to white on purpose — the body of the cloud
 * should read as white and only the shaded underside should carry hue.
 * shadowLift is deliberately small: the shadow colour is an ambient floor for
 * crevices, not a second light. At lift ~1 the shaded half saturates too and
 * the whole cloud flattens to one pale tone.
 */
private fun lookFor(state: OrbState): OrbLook = when (state) {
    OrbState.Idle -> OrbLook(
        light = Color(0xFFFFF3E4), shadow = Color(0xFF8C94C7),
        power = 4.2f, ambient = 0.10f, shadowLift = 0.26f, density = 1.2f, speed = 5.5f,
    )
    OrbState.Listening -> OrbLook(
        light = Color(0xFFFFF8F0), shadow = Color(0xFF8A96D6),
        power = 4.5f, ambient = 0.12f, shadowLift = 0.28f, density = 1.28f, speed = 8.5f,
    )
    OrbState.Thinking -> OrbLook(
        light = Color(0xFFF5EEFF), shadow = Color(0xFF9C90DE),
        power = 4.8f, ambient = 0.14f, shadowLift = 0.30f, density = 1.36f, speed = 12.0f,
    )
    OrbState.Speaking -> OrbLook(
        light = Color(0xFFFFEFDA), shadow = Color(0xFFB492D2),
        power = 5.2f, ambient = 0.17f, shadowLift = 0.33f, density = 1.44f, speed = 9.5f,
    )
}

/** Offscreen march resolution. 192 is the sweet spot between detail and cost. */
private const val ORB_RES = 192

/**
 * @param level 0..1 live amplitude — mic level while listening, output level
 *   while speaking. Ignored for [OrbState.Idle].
 * @param fps march refresh rate; the view itself still composites every frame.
 */
@Composable
fun Orb(
    modifier: Modifier = Modifier,
    state: OrbState = OrbState.Idle,
    level: Float = 0f,
    fps: Int = 30,
) {
    if (Build.VERSION.SDK_INT >= 33) {
        ShaderOrb(modifier = modifier, state = state, level = level, fps = fps)
    } else {
        CanvasOrb(modifier = modifier, state = state, level = level)
    }
}

// ---------- API 33+ : AGSL volumetric march ----------

private val ORB_AGSL = """
    uniform float2 iResolution;
    uniform float  iTime;
    uniform float  iInput;
    uniform float  iOutput;
    uniform float4 cLight;
    uniform float4 cShadow;
    uniform float  uPower;
    uniform float  uAmbient;
    uniform float  uShadowLift;
    uniform float  uDensity;

    float density(float3 p, float t, float dens) {
        float r = length(p);
        float3 q = p * 3.2;
        float f = 1.0;
        for (int k = 0; k < 5; k++) {
            q += cos(q.yzx * f + float3(t * 0.3)) / f;
            f *= 1.8;
        }
        float n = (sin(q.x) + sin(q.y) + sin(q.z)) / 3.0 * 0.5 + 0.5;
        // The surface itself is displaced by the noise — that is what gives
        // cumulus lobes. A smooth shell modulated from inside only ever
        // renders as a smooth ball with a wispy halo.
        float surf = 2.0 * (0.72 + 0.32 * n);
        float d = surf - r;
        if (d <= 0.0) return 0.0;
        return smoothstep(0.0, 0.55, d) * dens;
    }

    // Henyey-Greenstein. g > 0 biases scattering forward, which is what makes
    // the limb facing the light bloom. The 1/(4*PI) normalisation is dropped
    // and folded into uPower, same as the web shader.
    float phaseHG(float c, float g) {
        float g2 = g * g;
        return (1.0 - g2) / pow(max(1.0 + g2 - 2.0 * g * c, 0.0001), 1.5);
    }

    half4 main(float2 fragCoord) {
        float2 uv = (2.0 * fragCoord - iResolution) / min(iResolution.x, iResolution.y);
        float3 ro = float3(0.0, 0.0, -4.4);
        float3 rd = normalize(float3(uv, 1.8));
        float t = iTime;

        // Light stays mostly frontal with a slight sway: a lateral light
        // leaves half the orb in shadow colour, while a frontal one lets the
        // lit white body dominate and keeps shading to the underside lobes.
        float3 L = normalize(float3(cos(t * 0.12) * 0.30, 0.38, sin(t * 0.12) * 0.18 + 0.88));
        float phase = phaseHG(dot(rd, L), 0.5);

        // Start at the sphere's front face, not the camera — the steps before
        // it contribute nothing and at 30 of them they are expensive.
        float tStart = 4.4 - 2.0;
        float dt = 4.0 / 30.0;

        // Output turns the light up, input thickens the cloud. Both are
        // amplitudes. Churn is deliberately NOT volume-scaled: it multiplies
        // the accumulated clock into a phase, so scaling it by live volume
        // would turn every wobble into a phase jump the size of the clock.
        float power = uPower * (1.0 + 0.8 * iOutput);
        float dens = uDensity * (1.0 + 0.35 * iInput);

        float T = 1.0;
        float3 scattered = float3(0.0);
        for (int i = 0; i < 30; i++) {
            float tt = tStart + (float(i) + 0.5) * dt;
            float3 p = ro + rd * tt;
            float dn = density(p, t, dens);
            if (dn > 0.001) {
                // short march toward the light for self-shadowing
                float shadow = 1.0;
                float lstep = 1.0;
                for (int k = 1; k <= 2; k++) {
                    float3 lp = p + L * (float(k) - 0.5) * lstep;
                    shadow *= exp(-density(lp, t, dens) * lstep * 1.6);
                }
                // Shadow appears ONCE, inside the mix. Multiplying by it again
                // as a factor scales the shadowed end toward zero, so the cool
                // colour is always multiplied away and the cloud comes out
                // monochrome beige however it is tinted.
                float3 lit = mix(cShadow.rgb * uShadowLift, cLight.rgb, shadow);
                scattered += T * dn * dt * lit * phase * power;
                T *= exp(-dn * dt * 1.4);
                if (T < 0.02) break;
            }
        }

        // soft ambient body so the unlit side is not pure black
        float body = 1.0 - T;
        scattered += cShadow.rgb * body * uAmbient;

        // exponential tone map (tanh's job in the GLSL version); 1.2 is the
        // exposure the frozen icon pose is rendered at, so the two match.
        float3 col = float3(1.0) - exp(-scattered * 1.2);
        float a = clamp(body * 1.5, 0.0, 1.0);
        // Emitted light, so rgb is premultiplied — AGSL expects premultiplied.
        return half4(col * a, a);
    }
""".trimIndent()

@Composable
private fun ShaderOrb(
    modifier: Modifier,
    state: OrbState,
    level: Float,
    fps: Int,
) {
    val shader = remember { RuntimeShader(ORB_AGSL) }
    val brush = remember(shader) { ShaderBrush(shader) }
    // The march runs in a layer exactly ORB_RES px wide; graphicsLayer then
    // scales that layer's texture up. RuntimeShader needs the hardware path,
    // so the low-res buffer has to be a Compose layer, not a software bitmap.
    val resDp = with(LocalDensity.current) { ORB_RES.toDp() }
    var outer by remember { mutableStateOf(IntSize.Zero) }

    val look = lookFor(state)
    val glide = spring<Float>(dampingRatio = 0.85f, stiffness = 90f)
    val power by animateFloatAsState(look.power, glide, label = "orb-power")
    val ambient by animateFloatAsState(look.ambient, glide, label = "orb-ambient")
    val lift by animateFloatAsState(look.shadowLift, glide, label = "orb-lift")
    val density by animateFloatAsState(look.density, glide, label = "orb-density")
    val speed by animateFloatAsState(look.speed, glide, label = "orb-speed")
    val light by animateColorAsState(look.light, tween(620), label = "orb-light")
    val shadow by animateColorAsState(look.shadow, tween(620), label = "orb-shadow")

    val lvl = level.coerceIn(0f, 1f)
    // Mic thickens the cloud, agent output turns the light up.
    val input = if (state == OrbState.Listening || state == OrbState.Idle) lvl else lvl * 0.3f
    val output = if (state == OrbState.Speaking) lvl else if (state == OrbState.Thinking) 0.35f else lvl * 0.25f

    // Integrated clock. Integrating the rate (rather than scaling a phase) is
    // what keeps state changes gliding instead of jumping.
    // open on the pose the frozen launcher icon uses, then drift from it
    var t by remember { mutableFloatStateOf(21f) }
    LaunchedEffect(fps) {
        val minFrameNs = 1_000_000_000L / fps.coerceIn(8, 60)
        var lastNs = -1L
        var accNs = 0L
        while (true) {
            withFrameNanos { nowNs ->
                if (lastNs < 0L) lastNs = nowNs
                val dtNs = (nowNs - lastNs).coerceAtMost(100_000_000L)
                lastNs = nowNs
                accNs += dtNs
                if (accNs >= minFrameNs) {
                    accNs = 0L
                    val dt = dtNs / 1_000_000_000f
                    t = (t + dt * speed).coerceAtMost(1_000_000f)
                }
            }
        }
    }

    Box(
        modifier.onSizeChanged { outer = it },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .size(resDp)
                .graphicsLayer {
                    if (outer.width > 0 && outer.height > 0) {
                        scaleX = outer.width / ORB_RES.toFloat()
                        scaleY = outer.height / ORB_RES.toFloat()
                    }
                },
        ) {
            val time = t
            shader.setFloatUniform("iResolution", size.width, size.height)
            shader.setFloatUniform("iTime", time)
            shader.setFloatUniform("iInput", input)
            shader.setFloatUniform("iOutput", output)
            shader.setFloatUniform("uPower", power)
            shader.setFloatUniform("uAmbient", ambient)
            shader.setFloatUniform("uShadowLift", lift)
            shader.setFloatUniform("uDensity", density)
            shader.setFloatUniform("cLight", light.red, light.green, light.blue, 1f)
            shader.setFloatUniform("cShadow", shadow.red, shadow.green, shadow.blue, 1f)
            drawRect(brush = brush)
        }
    }
}

// ---------- API 24-32 : Canvas fallback ----------

/*
 * No AGSL before 33, so the volume is faked with additive soft blobs: a cool
 * body for the shaded side, then white clumps piled on the lit side. Same
 * palette and same churn driver as the shader, so the two read as one design.
 */
@Composable
private fun CanvasOrb(
    modifier: Modifier,
    state: OrbState,
    level: Float,
) {
    val look = lookFor(state)
    val glide = spring<Float>(dampingRatio = 0.85f, stiffness = 90f)
    val speed by animateFloatAsState(look.speed, glide, label = "orb-c-speed")
    val density by animateFloatAsState(look.density, glide, label = "orb-c-density")
    val power by animateFloatAsState(look.power, glide, label = "orb-c-power")
    val light by animateColorAsState(look.light, tween(620), label = "orb-c-light")
    val shadow by animateColorAsState(look.shadow, tween(620), label = "orb-c-shadow")
    val lvl = level.coerceIn(0f, 1f)

    // open on the pose the frozen launcher icon uses, then drift from it
    var t by remember { mutableFloatStateOf(21f) }
    LaunchedEffect(Unit) {
        var lastNs = -1L
        while (true) {
            withFrameNanos { nowNs ->
                if (lastNs < 0L) lastNs = nowNs
                val dt = ((nowNs - lastNs).coerceAtMost(100_000_000L)) / 1_000_000_000f
                lastNs = nowNs
                t = (t + dt * speed).coerceAtMost(1_000_000f)
            }
        }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f
        val time = t

        val circle = Path().apply {
            addOval(Rect(Offset(cx - r, cy - r), Size(r * 2f, r * 2f)))
        }
        clipPath(circle) {
            // cool unlit body
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        shadow.copy(alpha = 0.30f + 0.30f * (density / 1.6f)),
                        shadow.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )

            // light direction, matching the shader's orbit
            val lxA = cos(time * 0.12f) * 0.30f
            val lzA = sin(time * 0.12f) * 0.18f + 0.88f
            val litX = cx + lxA * r * 0.28f
            val litY = cy - 0.38f * r * 0.28f + lzA * r * 0.10f

            // additive white clumps
            val clumps = 20
            val bright = (power / 4.8f).coerceIn(0.55f, 1.3f)
            for (i in 0 until clumps) {
                val fi = i.toFloat()
                val a1 = fi * 2.399963f + time * 0.05f
                val rad = r * (0.12f + 0.62f * ((sin(fi * 1.7f + time * 0.11f) + 1f) / 2f))
                val bx = litX + cos(a1) * rad * 0.85f
                val by = litY + sin(a1 * 1.13f) * rad * 0.85f
                val br = r * (0.20f + 0.26f * ((cos(fi * 2.3f + time * 0.17f) + 1f) / 2f))
                val a = (0.05f + 0.13f * bright + 0.10f * lvl) *
                    (0.55f + 0.45f * ((sin(fi * 3.1f + time * 0.23f) + 1f) / 2f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(light.copy(alpha = a.coerceIn(0f, 0.5f)), Color.Transparent),
                        center = Offset(bx, by),
                        radius = br,
                    ),
                    radius = br,
                    center = Offset(bx, by),
                    blendMode = BlendMode.Plus,
                )
            }

            // hot core on the lit side so the volume reads as bright white
            val coreA = (0.34f + 0.30f * bright + 0.22f * lvl).coerceIn(0f, 0.92f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        light.copy(alpha = coreA),
                        light.copy(alpha = coreA * 0.35f),
                        Color.Transparent,
                    ),
                    center = Offset(litX, litY),
                    radius = r * 0.82f,
                ),
                radius = r * 0.82f,
                center = Offset(litX, litY),
                blendMode = BlendMode.Plus,
            )

            // limb bloom toward the light
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(shadow.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }
    }
}

// ---------- stage ----------

/**
 * The orb plus the air around it. The cloud is emissive, so it lights its own
 * surroundings: a breathing radial halo behind it, swelling with [level].
 * [accent] tints the halo — sage while the agent speaks, periwinkle otherwise.
 */
@Composable
fun OrbStage(
    modifier: Modifier = Modifier,
    state: OrbState = OrbState.Idle,
    level: Float = 0f,
    accent: Color = Color(0xFFC3CBFF),
    orbSize: Dp = 288.dp,
) {
    val bloom = rememberInfiniteTransition(label = "bloom")
    val breathe by bloom.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathe",
    )
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val s = breathe + level * 0.10f
                    scaleX = s
                    scaleY = s
                    alpha = 0.34f + level * 0.40f
                },
        ) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val rad = size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.40f), Color.Transparent),
                    center = c,
                    radius = rad,
                ),
                radius = rad,
                center = c,
            )
        }
        Orb(
            modifier = Modifier
                .size(orbSize)
                .graphicsLayer {
                    val s = 1f + level * 0.035f
                    scaleX = s
                    scaleY = s
                },
            state = state,
            level = level,
        )
    }
}
