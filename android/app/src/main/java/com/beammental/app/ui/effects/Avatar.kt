package com.beammental.app.ui.effects

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The Beam mascot, rendered from the user's avatar.avatar.json export
 * (bible-strong/avatar-definition v1): a data-driven playback engine with
 * real expressions, timed transitions, blinking and ambient micro-motion.
 */

@Serializable
private data class AvatarDef(
    val body: BodyDef = BodyDef(),
    val colors: ColorsDef = ColorsDef(),
    val expressions: Map<String, ExprDef> = emptyMap(),
    val animations: Map<String, AnimDef> = emptyMap(),
)

@Serializable
private data class BodyDef(val primary: SurfaceDef = SurfaceDef())

@Serializable
private data class SurfaceDef(
    val width: Float = 245f,
    val height: Float = 245f,
    val roundness: Float = 0.75f,
)

@Serializable
private data class ColorsDef(val body: String = "#ffffff", val eyes: String = "#111316")

@Serializable
private data class ExprDef(
    val head: Vec3 = Vec3(),
    val eyes: EyesDef = EyesDef(),
    val motion: MotionDef = MotionDef(),
)

@Serializable
private data class Vec3(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f)

@Serializable
private data class EyesDef(
    val left: EyeDef = EyeDef(),
    val right: EyeDef = EyeDef(),
    val spacing: Float = 35f,
)

@Serializable
private data class EyeDef(
    val width: Float = 20f,
    val height: Float = 50f,
    val x: Float = 0f,
    val y: Float = -7f,
    val angle: Float = 0f,
)

@Serializable
private data class MotionDef(val eyes: String = "none", val body: String = "none")

@Serializable
private data class AnimDef(
    @SerialName("playbackMode") val playbackMode: String = "loop",
    val steps: List<AnimStep> = emptyList(),
    val blink: BlinkDef = BlinkDef(),
)

@Serializable
private data class AnimStep(
    val expression: String = "neutral",
    val holdMs: Long = 1000,
    val transitionMs: Long = 300,
    val transition: String = "smooth",
)

@Serializable
private data class BlinkDef(
    val enabled: Boolean = false,
    val initialDelayMs: Long = 2000,
    val minIntervalMs: Long = 3000,
    val maxIntervalMs: Long = 5000,
    val durationMs: Long = 250,
)

/** Interpolated eye geometry in avatar units, ready to draw. */
private data class EyeGeom(
    val w: Float,
    val h: Float,
    val x: Float,
    val y: Float,
    val angle: Float,
)

// Brand look of the Beam mark — pinned to the launcher icon so the mascot
// reads as "our Beam" on every theme, not as theme-tinted geometry.
private val BodyBlueLight = Color(0xFF5FA9F8)
private val BodyBlueDeep = Color(0xFF1B63DE)

private data class AvatarFrame(
    val headX: Float = 0f,
    val headY: Float = 0f,
    val headZ: Float = 0f,
    val left: EyeGeom = EyeGeom(20f, 50f, -17.5f, -7f, 0f),
    val right: EyeGeom = EyeGeom(20f, 50f, 17.5f, -7f, 0f),
    val eyeColor: Color = Color(0xFF111316),
    val bodyRatio: Float = 0.75f,
)

private var cachedDef: AvatarDef? = null

private fun loadAvatar(context: Context): AvatarDef {
    cachedDef?.let { return it }
    val def = runCatching {
        val json = Json { ignoreUnknownKeys = true }
        context.assets.open("avatar.json").bufferedReader().use { it.readText() }
            .let { json.decodeFromString<AvatarDef>(it) }
    }.getOrNull() ?: AvatarDef()
    cachedDef = def
    return def
}

private fun parseColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color.White)

/** Easing curves ported 1:1 from @bible-strong/avatar-core. */
private fun ease(kind: String, t: Float): Float = when (kind) {
    "smooth" -> t * t * (3f - 2f * t)
    "snappy" -> 1f - (1f - t).pow(3)
    else -> {
        val d = 1f - exp(-6f) * cos(8f)
        ((1f - exp(-6f * t) * cos(8f * t)) / d).coerceIn(0f, 1f)
    }
}

private fun hash1(n: Float): Float {
    val v = sin(n * 127.1f + 311.7f) * 43758.5453f
    return (v - floor(v)) * 2f - 1f
}

private fun smooth01(t: Float) = t * t * (3f - 2f * t)

/** Smooth value noise over an integer lattice (time in ms). */
private fun vnoise(time: Float, channel: Float, seed: Float, period: Float): Float {
    val i = floor(time / period)
    val f = time / period - i
    val s = smooth01(f)
    val a = hash1(i * 3f + channel + seed)
    val b = hash1((i + 1f) * 3f + channel + seed)
    return a + (b - a) * s
}

/** Micro-saccade: quick 140 ms jump, then hold until the next period. */
private fun saccade(time: Float, channel: Float): Float {
    val period = 1100f
    if (time <= 0f) return 0f
    val i = floor(time / period)
    val f = (time - i * period) / 140f
    val c = smooth01(min(f, 1f))
    val a = if (i == 0f) 0f else hash1((i - 1f) * 2f + channel + 17.29f)
    val b = hash1(i * 2f + channel + 17.29f)
    return a + (b - a) * c
}

/**
 * Playback engine: steps with hold/transition phases, loop/once/pingPong,
 * blinking and ambient motion, all ported from avatar-core's runtime.
 */
private class AvatarEngine(private val def: AvatarDef) {

    private var pendingAnim: String? = null
    private var anim: AnimDef? = null
    private var stepIndex = 0
    private var direction = 1
    private var phase = 0 // 0 = transition, 1 = hold
    private var phaseStart = 0L
    private var activeExpr = "neutral"
    private var transitionFrom: String? = null
    private var blinkDue = -1L
    private var blinkStart = -1L

    private val neutral: ExprDef
        get() = def.expressions["neutral"] ?: ExprDef()

    fun requestAnimation(key: String) {
        pendingAnim = key
    }

    private fun start(key: String, now: Long) {
        val a = def.animations[key]
            ?: def.animations["idle"]
            ?: return
        anim = a
        stepIndex = 0
        direction = 1
        phase = 0
        phaseStart = now
        transitionFrom = activeExpr // blend from wherever we are
        activeExpr = a.steps.firstOrNull()?.expression ?: "neutral"
        blinkDue = if (a.blink.enabled) now + a.blink.initialDelayMs else -1L
        blinkStart = -1
    }

    private fun advance(now: Long) {
        pendingAnim?.let { start(it, now); pendingAnim = null }
        val a = anim ?: return
        if (a.blink.enabled && blinkDue in 0..now) {
            val interval = a.blink.minIntervalMs +
                (Math.random() * (a.blink.maxIntervalMs - a.blink.minIntervalMs)).toLong()
            blinkStart = blinkDue
            blinkDue = blinkDue + a.blink.durationMs + interval
        }
        var guard = a.steps.size * 4 + 4
        while (guard-- > 0) {
            val step = a.steps.getOrNull(stepIndex) ?: break
            val dur = if (phase == 0) step.transitionMs else step.holdMs
            if (now < phaseStart + dur) break
            phaseStart += dur
            if (phase == 0) {
                phase = 1
                continue
            }
            val last = a.steps.size - 1
            var newIdx = stepIndex
            var newDir = direction
            var complete = false
            if (stepIndex < last && direction == 1) newIdx++
            else if (stepIndex > 0 && direction == -1) newIdx--
            else if (a.playbackMode == "once") complete = true
            else if (a.playbackMode == "pingPong" && last > 0) {
                newDir = -direction
                newIdx = stepIndex + newDir
            } else {
                newIdx = 0
                newDir = 1
            }
            if (complete) break
            stepIndex = newIdx
            direction = newDir
            phase = 0
            transitionFrom = activeExpr
            activeExpr = a.steps[newIdx].expression
        }
    }

    fun frame(now: Long): AvatarFrame {
        advance(now)

        val to = def.expressions[activeExpr] ?: neutral
        var headX = to.head.x
        var headY = to.head.y
        var headZ = to.head.z
        var wL = to.eyes.left.width
        var wR = to.eyes.right.width
        var hL = to.eyes.left.height
        var hR = to.eyes.right.height
        var spacing = to.eyes.spacing
        var xL = to.eyes.left.x
        var xR = to.eyes.right.x
        var yL = to.eyes.left.y
        var yR = to.eyes.right.y
        var aL = to.eyes.left.angle
        var aR = to.eyes.right.angle

        // transition blend from the previous expression
        val a = anim
        if (a != null && phase == 0) {
            val step = a.steps.getOrNull(stepIndex)
            val from = transitionFrom?.let { def.expressions[it] }
            if (step != null && from != null && step.transitionMs > 0) {
                val t = ease(step.transition, ((now - phaseStart).toFloat() / step.transitionMs).coerceIn(0f, 1f))
                headX = from.head.x + (headX - from.head.x) * t
                headY = from.head.y + (headY - from.head.y) * t
                headZ = from.head.z + (headZ - from.head.z) * t
                wL = from.eyes.left.width + (wL - from.eyes.left.width) * t
                wR = from.eyes.right.width + (wR - from.eyes.right.width) * t
                hL = from.eyes.left.height + (hL - from.eyes.left.height) * t
                hR = from.eyes.right.height + (hR - from.eyes.right.height) * t
                spacing = from.eyes.spacing + (spacing - from.eyes.spacing) * t
                xL = from.eyes.left.x + (xL - from.eyes.left.x) * t
                xR = from.eyes.right.x + (xR - from.eyes.right.x) * t
                yL = from.eyes.left.y + (yL - from.eyes.left.y) * t
                yR = from.eyes.right.y + (yR - from.eyes.right.y) * t
                aL = from.eyes.left.angle + (aL - from.eyes.left.angle) * t
                aR = from.eyes.right.angle + (aR - from.eyes.right.angle) * t
            }
        }

        // ambient micro-motion (port of applyAmbientMotion)
        val t = now.toFloat()
        val seed = headX * 0.71f + headY * 1.13f + headZ * 1.37f
        var sacX = 0f
        var sacY = 0f
        if (to.motion.body == "slowDrift") {
            headX += vnoise(t, 0f, seed, 2600f) * 0.8f
            headY += vnoise(t, 1f, seed, 3300f) * 1.15f
            headZ += vnoise(t, 2f, seed, 4100f) * 0.45f
        } else if (to.motion.body == "shake") {
            val ts = t / 1000f
            headX += (sin(ts * 31f) + sin(ts * 53f) * 0.45f) * 1.15f
            headY += (sin(ts * 37f) + sin(ts * 61f) * 0.4f) * 1.35f
            headZ += sin(ts * 43f) * 0.7f
        }
        if (to.motion.eyes == "microSaccades") {
            sacX = saccade(t, 0f) * 1.5f
            sacY = saccade(t, 1f) * 0.9f
        } else if (to.motion.eyes == "shake") {
            val ts = t / 1000f
            sacX = (sin(ts * 47f) + sin(ts * 71f) * 0.45f) * 1.2f
            sacY = (sin(ts * 59f) + sin(ts * 83f) * 0.4f) * 0.8f
        }

        // blink: triangular 1 -> 0 -> 1 over durationMs
        var blink = 1f
        if (a != null && a.blink.enabled && blinkStart >= 0) {
            val r = (now - blinkStart).toFloat() / a.blink.durationMs
            if (r in 0f..1f) blink = abs(r * 2f - 1f)
        }

        return AvatarFrame(
            headX = headX,
            headY = headY,
            headZ = headZ,
            left = EyeGeom(wL, hL * blink, -spacing / 2f + xL + sacX, yL + sacY, aL),
            right = EyeGeom(wR, hR * blink, spacing / 2f + xR + sacX, yR + sacY, aR),
            eyeColor = parseColor(def.colors.eyes),
            bodyRatio = def.body.primary.roundness,
        )
    }
}

/**
 * The animated Beam mascot. Draws the avatar from avatar.json assets:
 * head tilt in 3D, expressive eyes, blinking and ambient micro-motion.
 */
@Composable
fun MascotBlob(
    modifier: Modifier = Modifier,
    blobSize: Dp = 40.dp,
    thinking: Boolean = false,
    animation: String? = null,
) {
    val context = LocalContext.current
    val def = remember { loadAvatar(context) }
    val engine = remember { AvatarEngine(def) }
    val animKey = animation ?: if (thinking) "thinking" else "idle"
    var frame by remember { mutableStateOf(AvatarFrame()) }

    LaunchedEffect(animKey) {
        engine.requestAnimation(animKey)
        while (true) {
            val t = withFrameNanos { it } / 1_000_000
            frame = engine.frame(t)
        }
    }

    Box(
        modifier
            .size(blobSize)
            .graphicsLayer {
                rotationX = frame.headX
                rotationY = frame.headY
                rotationZ = frame.headZ
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            },
    ) {
        Canvas(Modifier.matchParentSize()) {
            val d = min(size.width, size.height)
            val s = d / 245f
            val cx = size.width / 2f
            val cy = size.height / 2f

            // body: the Beam brand mark — blue gradient on a rounded-cube
            // front face, matching the launcher icon: light face top-left,
            // deep face bottom-right, soft sheen where the light lands.
            val bodyW = 232f * s
            val bodyH = 232f * s
            val topLeft = Offset(cx - bodyW / 2f, cy - bodyH / 2f)
            val corner = bodyW * frame.bodyRatio / 2f
            val bodyPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(topLeft, Size(bodyW, bodyH)),
                        cornerRadius = CornerRadius(corner, corner),
                    ),
                )
            }

            clipPath(bodyPath) {
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(BodyBlueLight, BodyBlueDeep),
                        start = topLeft,
                        end = Offset(topLeft.x + bodyW, topLeft.y + bodyH),
                    ),
                    topLeft = topLeft,
                    size = Size(bodyW, bodyH),
                    cornerRadius = CornerRadius(corner, corner),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                        center = Offset(topLeft.x + bodyW * 0.26f, topLeft.y + bodyH * 0.18f),
                        radius = bodyW * 0.8f,
                    ),
                    center = Offset(topLeft.x + bodyW * 0.26f, topLeft.y + bodyH * 0.18f),
                    radius = bodyW * 0.8f,
                )
            }

            // eyes: vertical pills, per-expression geometry
            listOf(frame.left, frame.right).forEach { eye ->
                val w = eye.w * s
                val h = eye.h * s
                val ex = cx + eye.x * s
                val ey = cy + eye.y * s
                rotate(degrees = eye.angle, pivot = Offset(ex, ey)) {
                    drawRoundRect(
                        color = frame.eyeColor,
                        topLeft = Offset(ex - w / 2f, ey - h / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(w / 2f, w / 2f),
                    )
                }
            }
        }
    }
}
