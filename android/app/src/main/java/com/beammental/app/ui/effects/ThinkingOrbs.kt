package com.beammental.app.ui.effects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beammental.app.ui.theme.BeamColors
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Thinking orbs — Beam's port of libraries.dev `thinking-orbs` 0.3.1.
 *
 * Transcribed from the upstream native port (ThinkingOrbsKit): the same
 * projected dot engine, the same `baseProfiles` / `presets` numbers from
 * `orbs-spec.json`, the same cull-at-0.02-alpha, radius floor and far-to-near
 * z-sort. Four of the nine states are carried over — the four a chat app
 * actually has: `working` (orbits), `listening` (wave), `breathing` (ring) and
 * `connecting` (web).
 *
 * Two deliberate deviations. Ink is a lerp between the live theme's Fog and
 * Mist rather than a raw grey, so the orbs belong to the palette and follow a
 * theme switch; and the frame is built into a pooled buffer instead of fresh
 * arrays, because a 64px ring emits ~500 dots per frame and this runs while
 * the model streams.
 */

/** Which of the ported modes to run. Upstream calls this `OrbState`; Beam's
 *  voice orb in Orb.kt already owns that name. */
enum class ThoughtState(val mode: OrbMode, val label: String) {
    Working(OrbMode.Orbits, "Beam pracuje"),
    Listening(OrbMode.Wave, "Beam počúva"),
    Breathing(OrbMode.Ring, "Beam premýšľa"),
    Connecting(OrbMode.Web, "Beam sa pripája"),
}

enum class OrbMode { Orbits, Wave, Ring, Web }

/** The two tuned sizes in orbs-spec.json. `displaySize` renders any other
 *  diameter while keeping the preset's geometry. */
enum class OrbSize(val units: Int) { Px20(20), Px64(64) }

private class Dot {
    var x = 0.0; var y = 0.0; var z = 0.0; var r = 0.0; var white = 0.0; var a = 1.0
    fun set(x: Double, y: Double, z: Double, r: Double, white: Double, a: Double) {
        this.x = x; this.y = y; this.z = z; this.r = r; this.white = white; this.a = a
    }
}

private class Line {
    var x1 = 0.0; var y1 = 0.0; var x2 = 0.0; var y2 = 0.0
    var white = 0.0; var a = 0.0; var w = 0.0
}

/** Pooled frame buffer: cleared and refilled every tick, never reallocated. */
private class OrbFrame {
    val dots = ArrayList<Dot>(64)
    val lines = ArrayList<Line>(16)
    private val dotPool = ArrayList<Dot>(64)
    private val linePool = ArrayList<Line>(16)
    private var usedDots = 0
    private var usedLines = 0

    fun reset() {
        dots.clear(); lines.clear(); usedDots = 0; usedLines = 0
    }

    fun dot(x: Double, y: Double, z: Double, r: Double, white: Double, a: Double = 1.0) {
        val d = if (usedDots < dotPool.size) dotPool[usedDots] else Dot().also { dotPool.add(it) }
        usedDots++
        d.set(x, y, z, r, white, a)
        dots.add(d)
    }

    fun line(x1: Double, y1: Double, x2: Double, y2: Double, white: Double, a: Double, w: Double) {
        val l = if (usedLines < linePool.size) linePool[usedLines] else Line().also { linePool.add(it) }
        usedLines++
        l.x1 = x1; l.y1 = y1; l.x2 = x2; l.y2 = y2; l.white = white; l.a = a; l.w = w
        lines.add(l)
    }

    /** Drop invisible marks, clamp radii to the mode's floor, z-sort far→near.
     *  Kotlin's sort is stable, which reproduces the web's draw order on modes
     *  that emit co-planar dots. */
    fun finalize(rMin: Double) {
        dots.removeAll { it.a < 0.02 }
        for (d in dots) if (d.r < rMin) d.r = rMin
        dots.sortWith(compareBy { it.z })
        lines.removeAll { it.a < 0.02 }
    }
}

/* ── shared primitives (src/engine/core.ts) ─────────────────────────────── */

private fun hashD(a: Double, b: Double): Double {
    val h = sin(a * 12.9898 + b * 78.233) * 43758.5453
    return h - floor(h)
}

private fun vnoise(x: Double, y: Double): Double {
    val xi = floor(x); val yi = floor(y)
    var fx = x - xi; var fy = y - yi
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    val a = hashD(xi, yi); val b = hashD(xi + 1, yi)
    val c = hashD(xi, yi + 1); val d = hashD(xi + 1, yi + 1)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
}

private fun fibDir(i: Int, n: Int, out: DoubleArray) {
    val golden = Math.PI * (3 - sqrt(5.0))
    val y = 1 - (2 * (i + 0.5)) / n
    val rad = sqrt(1 - y * y)
    val a = i * golden
    out[0] = rad * cos(a); out[1] = y; out[2] = rad * sin(a)
}

private fun lerp(a: Double, b: Double, f: Double) = a + (b - a) * f
private fun frac(x: Double) = x - floor(x)

/** Spin + tilt + orthographic projection. Projects into a reused array — the
 *  inner loops emit thousands of points per frame. */
private class Projector(
    yaw: Double,
    tilt: Double,
    private val cx: Double,
    private val cy: Double,
    private val scale: Double,
) {
    private val st = sin(tilt); private val ct = cos(tilt)
    private val sy = sin(yaw); private val cyw = cos(yaw)
    private val out = DoubleArray(3)

    operator fun invoke(x: Double, y: Double, z: Double): DoubleArray {
        val x1 = x * cyw + z * sy
        val z1 = -x * sy + z * cyw
        val y1 = y * ct - z1 * st
        val z2 = y * st + z1 * ct
        out[0] = cx + x1 * scale
        out[1] = cy - y1 * scale
        out[2] = z2
        return out
    }
}

/** Dot radii were tuned for a 300-unit frame; sub-linear scaling keeps small
 *  spinners legible. */
private fun radiusScale(size: Double, p: Double) = (size / 300).pow(p)

/* ── preset resolution (src/presets.ts) ─────────────────────────────────── */

private class ResolvedPreset(val speed: Double, val opts: Map<String, Double>)

private val COUNT_PAIRS = listOf("rings" to "lonDensity", "lanes" to "segs")
private val COUNT_KEYS = listOf("orbitN", "ghostN", "nodeN", "signals")
private val RADIUS_KEYS = listOf("rBase", "rDepth", "ghostR", "partR", "partRDepth", "nodeR", "nodeRDepth")

private fun baseProfile(mode: OrbMode): Map<String, Double> = when (mode) {
    OrbMode.Orbits -> mapOf(
        "orbitN" to 12.0, "ghostN" to 40.0, "ghostR" to 0.9, "ghostA" to 0.5,
        "particles" to 3.0, "partR" to 1.2, "partRDepth" to 1.6,
        "rsPow" to 0.6, "rMin" to 0.3,
    )
    OrbMode.Wave -> mapOf(
        "rings" to 15.0, "lonDensity" to 40.0, "rBase" to 0.6, "rDepth" to 1.7,
        "rsPow" to 0.6, "rMin" to 0.3,
    )
    OrbMode.Ring -> mapOf(
        "lanes" to 5.0, "segs" to 88.0, "ghostN" to 0.0, "faceOn" to 1.0,
        "rBase" to 1.1, "rDepth" to 1.7, "rsPow" to 0.6, "rMin" to 0.3,
    )
    OrbMode.Web -> mapOf(
        "nodeN" to 30.0, "thr" to 0.72, "signals" to 5.0, "nodeR" to 1.4,
        "nodeRDepth" to 1.8, "lineW" to 0.8, "rsPow" to 0.6, "rMin" to 0.3,
    )
}

/** `presets[mode][size]`: speed, count scale, radius scale, then per-mode
 *  extras. Numbers verbatim from orbs-spec.json. */
private fun resolvePreset(state: ThoughtState, size: OrbSize): ResolvedPreset {
    val mode = state.mode
    val small = size == OrbSize.Px20
    val speed: Double
    val count: Double
    val radius: Double
    val extra = HashMap<String, Double>()
    when (mode) {
        OrbMode.Orbits -> if (small) {
            speed = 3.9; count = 0.238; radius = 2.4
        } else { speed = 1.885; count = 1.0; radius = 1.0 }
        OrbMode.Wave -> if (small) {
            speed = 3.998; count = 0.105; radius = 1.6
        } else { speed = 4.388; count = 0.341; radius = 1.0 }
        OrbMode.Ring -> if (small) {
            speed = 3.78; count = 0.028; radius = 1.622
            extra["spin"] = 0.0; extra["bandMul"] = 3.968; extra["wobMul"] = 0.565
        } else {
            speed = 3.24; count = 0.25; radius = 0.956
            extra["spin"] = 0.0; extra["bandMul"] = 3.627; extra["wobMul"] = 0.368
        }
        OrbMode.Web -> if (small) {
            speed = 6.63; count = 0.25; radius = 1.52
        } else { speed = 3.315; count = 1.35; radius = 0.95 }
    }

    val opts = HashMap(baseProfile(mode))
    if (count != 1.0) {
        val done = HashSet<String>()
        val rt = sqrt(count)
        for ((a, b) in COUNT_PAIRS) {
            val va = opts[a]; val vb = opts[b]
            if (va != null && vb != null && a !in done && b !in done) {
                opts[a] = max(2.0, round(va * rt))
                opts[b] = max(2.0, round(vb * rt))
                done += a; done += b
            }
        }
        for (k in COUNT_KEYS) {
            // an explicit 0 means the mode opted out of that layer — scaling
            // must not resurrect it as a single stray dot
            val v = opts[k]
            if (v != null && v != 0.0 && k !in done) opts[k] = max(1.0, round(v * count))
        }
    }
    if (radius != 1.0) for (k in RADIUS_KEYS) opts[k]?.let { opts[k] = it * radius }
    opts += extra
    return ResolvedPreset(speed, opts)
}

private fun Map<String, Double>.d(key: String, def: Double) = this[key] ?: def
private fun Map<String, Double>.i(key: String, def: Int) = (this[key] ?: def.toDouble()).roundToInt()

/* ── modes ──────────────────────────────────────────────────────────────── */

/** Orbits: particles on tilted orbits — "working". No nucleus; just ghost
 *  paths and the particles doing the work. */
private fun frameOrbits(size: Double, t: Double, o: Map<String, Double>, f: OrbFrame) {
    val half = size / 2
    val r = half * 0.82
    val pt = Projector(yaw = t * 0.12, tilt = 0.3, cx = half, cy = half, scale = 1.0)
    val rs = radiusScale(size, o.d("rsPow", 0.6))
    val orbitN = o.i("orbitN", 12)
    val ghostN = o.i("ghostN", 40)
    val particles = o.i("particles", 3)

    for (orb in 0 until orbitN) {
        val h1 = hashD(orb.toDouble(), 1.7)
        val h2 = hashD(orb.toDouble(), 5.2)
        val h3 = hashD(orb.toDouble(), 8.9)
        val ro = r * (0.45 + 0.52 * h1)
        val th = h1 * 2 * Math.PI
        val phi = acos(2 * h2 - 1)
        // orbit plane basis (u, v perpendicular to normal n)
        val nx = sin(phi) * cos(th); val ny = cos(phi); val nz = sin(phi) * sin(th)
        var ux = -ny; var uy = nx; val uz = 0.0
        val ul = max(1e-6, sqrt(ux * ux + uy * uy))
        ux /= ul; uy /= ul
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux
        val speed = (0.25 + 0.55 * h3) * (if (h3 > 0.5) 1 else -1)

        for (k in 0 until ghostN) {
            val a = (k.toDouble() / ghostN) * 2 * Math.PI
            val ca = cos(a); val sa = sin(a)
            val p = pt((ux * ca + vx * sa) * ro, (uy * ca + vy * sa) * ro, (uz * ca + vz * sa) * ro)
            val depth = (p[2] / ro + 1) / 2
            f.dot(p[0], p[1], p[2], o.d("ghostR", 0.9) * rs, 0.72, o.d("ghostA", 0.5) * (0.4 + 0.6 * depth))
        }
        for (m in 0 until particles) {
            val a = t * speed + (m.toDouble() / particles) * 2 * Math.PI + h2 * 6
            val ca = cos(a); val sa = sin(a)
            val p = pt((ux * ca + vx * sa) * ro, (uy * ca + vy * sa) * ro, (uz * ca + vz * sa) * ro)
            val depth = (p[2] / ro + 1) / 2
            f.dot(
                p[0], p[1], p[2],
                (o.d("partR", 1.2) + o.d("partRDepth", 1.6) * depth) * rs,
                0.3 - 0.22 * depth,
            )
        }
    }
}

/** Wave: a waveform rolls through the rings — "listening". */
private fun frameWave(size: Double, t: Double, o: Map<String, Double>, f: OrbFrame) {
    val half = size / 2
    // 0.76 base x 1.15: the undulation pulls the sphere inward, so wave reads
    // ~15% smaller than the other lattice modes and is scaled up to match
    val r = half * 0.874
    val pt = Projector(yaw = t * 0.18, tilt = 0.38, cx = half, cy = half, scale = 1.0)
    val rs = radiusScale(size, o.d("rsPow", 0.6))
    val rings = o.i("rings", 15)
    val lonDensity = o.d("lonDensity", 40.0)

    for (ri in 0..rings) {
        val lat = -Math.PI / 2 + (ri.toDouble() / rings) * Math.PI
        val cosLat = cos(lat); val sinLat = sin(lat)
        // two waves at different tempi — organic, never quite repeating
        val w = 0.62 * sin(t * 2.1 - ri * 0.52) + 0.38 * sin(t * 1.27 + ri * 0.83)
        val rr = r * (0.88 + 0.105 * w)
        val lonCount = max(1, round(abs(cosLat) * lonDensity).roundToInt())
        for (lj in 0 until lonCount) {
            val lon = (lj.toDouble() / lonCount) * 2 * Math.PI
            val p = pt(cosLat * cos(lon) * rr, sinLat * rr, cosLat * sin(lon) * rr)
            val depth = (p[2] / r + 1) / 2
            val crest = max(0.0, w)
            f.dot(
                p[0], p[1], p[2],
                (o.d("rBase", 0.6) + o.d("rDepth", 1.7) * depth) * (1 + 0.4 * crest) * rs,
                0.66 - 0.56 * depth - 0.1 * crest,
            )
        }
    }
}

/** Ring: a face-on circle whose radius undulates — "breathing". Shares the
 *  ribbon geometry; `faceOn` modulates the in-plane radius so lobes genuinely
 *  swell outward instead of being cancelled by the renormalisation. */
private fun frameRing(size: Double, t: Double, o: Map<String, Double>, f: OrbFrame) {
    val half = size / 2
    val r = half * 0.78
    val spin = o.d("spin", 1.0)
    val camTilt = 0.3
    val faceOn = o.d("faceOn", 0.0) != 0.0
    val pt = Projector(yaw = t * 0.1 * spin, tilt = camTilt, cx = half, cy = half, scale = 1.0)
    val rs = radiusScale(size, o.d("rsPow", 0.6))

    val ghostN = o.i("ghostN", 150)
    val dir = DoubleArray(3)
    if (ghostN > 0) {
        for (i in 0 until ghostN) {
            fibDir(i, ghostN, dir)
            val p = pt(dir[0] * r, dir[1] * r, dir[2] * r)
            val depth = (p[2] / r + 1) / 2
            f.dot(p[0], p[1], p[2], 0.8 * rs, 0.78, 0.1 + 0.22 * depth)
        }
    }

    // spin = 0 freezes the band's orientation, leaving only the travelling
    // undulation; ta = -camTilt cancels the projection's vertical squash so the
    // band reads as a true circle rather than a tilted ellipse
    val ya = t * 0.24 * spin
    val ta = if (faceOn) -camTilt else 0.55 + 0.3 * sin(t * 0.18) * spin
    val ux = cos(ya); val uy = 0.0; val uz = sin(ya)
    val vx = -uz * sin(ta); val vy = cos(ta); val vz = ux * sin(ta)
    val nx = uy * vz - uz * vy
    val ny = uz * vx - ux * vz
    val nz = ux * vy - uy * vx

    val wobMul = o.d("wobMul", 1.0)
    val wobAmp = 0.23 * wobMul
    val baseR = if (faceOn) r / (1 + 0.85 * wobAmp) else r

    val bandMul = o.d("bandMul", 1.0)
    val lanes = max(1, round(o.d("lanes", 5.0) * bandMul).roundToInt())
    val segs = o.i("segs", 88)
    for (w in 0 until lanes) {
        val laneOff = (w - (lanes - 1) / 2.0) * 0.075
        val edge = abs(w - (lanes - 1) / 2.0) / max(1.0, (lanes - 1) / 2.0)
        for (k in 0 until segs) {
            val a = (k.toDouble() / segs) * 2 * Math.PI
            val wob = (0.16 * sin(a * 3 - t * 1.7 + w * 0.22) + 0.07 * sin(a * 5 + t * 1.1)) * wobMul
            val radial = if (faceOn) 1 + wob else 1.0
            val off = if (faceOn) laneOff else laneOff + wob
            val x = ux * cos(a) + vx * sin(a) + nx * off
            val y = uy * cos(a) + vy * sin(a) + ny * off
            val z = uz * cos(a) + vz * sin(a) + nz * off
            val l = sqrt(x * x + y * y + z * z)
            val rr = baseR * radial
            val p = pt((x / l) * rr, (y / l) * rr, (z / l) * rr)
            val depth = (p[2] / r + 1) / 2
            f.dot(
                p[0], p[1], p[2],
                (o.d("rBase", 1.1) + o.d("rDepth", 1.7) * depth) * (1 - 0.25 * edge) * rs,
                0.52 - 0.44 * depth + 0.18 * edge,
                0.4 + 0.6 * depth,
            )
        }
    }
}

/** Web: a constellation wires itself — "connecting". Nodes drift on the sphere
 *  under slow value noise, close pairs grow an edge, bright packets run along
 *  randomly re-picked pairs. */
private fun frameWeb(size: Double, t: Double, o: Map<String, Double>, f: OrbFrame) {
    val half = size / 2
    val r = half * 0.8 * o.d("spread", 1.0)
    // the projector carries the radius as its scale, so node vectors stay
    // unit-length and the distances below are in unit-sphere space
    val pt = Projector(yaw = t * 0.12, tilt = 0.32, cx = half, cy = half, scale = r)
    val rs = radiusScale(size, o.d("rsPow", 0.6))
    val nodeN = o.i("nodeN", 30)
    val thr = o.d("thr", 0.72)
    val nodeR = o.d("nodeR", 1.4)
    val nodeRDepth = o.d("nodeRDepth", 1.8)

    val nodes = Array(nodeN) { DoubleArray(3) }
    val dir = DoubleArray(3)
    for (i in 0 until nodeN) {
        fibDir(i, nodeN, dir)
        val x = dir[0] + 0.3 * (vnoise(i * 0.31 + 9, t * 0.24) - 0.5) * 2
        val y = dir[1] + 0.3 * (vnoise(i * 0.53 + 27, t * 0.21) - 0.5) * 2
        val z = dir[2] + 0.3 * (vnoise(i * 0.77 + 55, t * 0.27) - 0.5) * 2
        val l = sqrt(x * x + y * y + z * z)
        nodes[i][0] = x / l; nodes[i][1] = y / l; nodes[i][2] = z / l
    }

    for (i in 0 until nodeN) {
        for (j in (i + 1) until nodeN) {
            val dx = nodes[i][0] - nodes[j][0]
            val dy = nodes[i][1] - nodes[j][1]
            val dz = nodes[i][2] - nodes[j][2]
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist >= thr) continue
            // the projector returns one reused array, so each endpoint is
            // copied out before the next projection overwrites it
            val a = pt(nodes[i][0], nodes[i][1], nodes[i][2])
            val x1 = a[0]; val y1 = a[1]; val z1 = a[2]
            val b = pt(nodes[j][0], nodes[j][1], nodes[j][2])
            val x2 = b[0]; val y2 = b[1]; val z2 = b[2]
            val depth = ((z1 + z2) / 2 + 1) / 2
            f.line(
                x1, y1, x2, y2, 0.42,
                (1 - dist / thr) * (0.3 + 0.55 * depth),
                max(0.6, o.d("lineW", 0.8) * rs),
            )
        }
    }

    for (i in 0 until nodeN) {
        val p = pt(nodes[i][0], nodes[i][1], nodes[i][2])
        val depth = (p[2] + 1) / 2
        val pulse = 1 + 0.25 * sin(t * 1.4 + i * 2.7)
        f.dot(p[0], p[1], p[2], (nodeR + nodeRDepth * depth) * pulse * rs, 0.55 - 0.45 * depth)
    }

    val signals = o.i("signals", 5)
    for (s in 0 until signals) {
        val seg = floor(t * 0.55 + s * 7.31)
        val a = floor(hashD(seg, s * 3.1 + 1.7) * nodeN).roundToInt()
        val b = floor(hashD(seg, s * 5.7 + 4.2) * nodeN).roundToInt()
        if (a == b || a >= nodeN || b >= nodeN) continue
        val f2 = frac(t * 0.55 + s * 7.31)
        val x = lerp(nodes[a][0], nodes[b][0], f2)
        val y = lerp(nodes[a][1], nodes[b][1], f2)
        val z = lerp(nodes[a][2], nodes[b][2], f2)
        val l = max(1e-6, sqrt(x * x + y * y + z * z))
        val p = pt(x / l, y / l, z / l)
        val depth = (p[2] + 1) / 2
        f.dot(p[0], p[1], p[2], (nodeR * 1.5 + nodeRDepth * depth) * rs, 0.05, 0.5 + 0.5 * depth)
    }
}

private fun buildFrame(mode: OrbMode, size: Double, t: Double, o: Map<String, Double>, f: OrbFrame) {
    when (mode) {
        OrbMode.Orbits -> frameOrbits(size, t, o, f)
        OrbMode.Wave -> frameWave(size, t, o, f)
        OrbMode.Ring -> frameRing(size, t, o, f)
        OrbMode.Web -> frameWeb(size, t, o, f)
    }
}

/**
 * A dotted thought-orb.
 *
 * @param displaySize renders an arbitrary diameter while keeping the tuned
 *   preset's geometry, so the drawing stays vector-crisp at any scale.
 * @param paused freezes on one frame and stops reading the clock entirely —
 *   an off-screen or settled orb costs nothing.
 */
@Composable
fun ThinkingOrb(
    modifier: Modifier = Modifier,
    state: ThoughtState = ThoughtState.Working,
    size: OrbSize = OrbSize.Px20,
    displaySize: Dp? = null,
    speed: Float = 1f,
    paused: Boolean = false,
    label: String = state.label,
) {
    val preset = remember(state, size) { resolvePreset(state, size) }
    val frame = remember { OrbFrame() }
    val side = displaySize ?: size.units.dp
    val effSpeed = preset.speed * speed
    val units = size.units

    Canvas(
        modifier
            .size(side)
            .semantics { contentDescription = label },
    ) {
        // every orb shares EffectClock, so several on screen stay in phase
        val t = if (EffectClock.reducedMotion || paused) {
            EffectClock.FROZEN_T * effSpeed
        } else {
            EffectClock.t * effSpeed
        }
        val u = this.size.width / units
        val fog = BeamColors.Fog
        val mist = BeamColors.Mist

        frame.reset()
        buildFrame(state.mode, units.toDouble(), t, preset.opts, frame)
        frame.finalize(preset.opts.d("rMin", 0.3))

        for (l in frame.lines) {
            drawLine(
                color = ink(l.white, l.a, fog, mist),
                start = Offset((l.x1 * u).toFloat(), (l.y1 * u).toFloat()),
                end = Offset((l.x2 * u).toFloat(), (l.y2 * u).toFloat()),
                strokeWidth = max(0.4f, (l.w * u).toFloat()),
            )
        }
        for (d in frame.dots) {
            drawCircle(
                color = ink(d.white, d.a, fog, mist),
                radius = max(0.15f, (d.r * u).toFloat()),
                center = Offset((d.x * u).toFloat(), (d.y * u).toFloat()),
            )
        }
    }
}

/** `grey = (dark ? 1 - white : white) * 255`, quantised to 8-bit exactly as the
 *  canvas painter does — then mapped onto the live palette instead of a raw
 *  grey, so the orbs follow a theme switch. */
private fun ink(white: Double, alpha: Double, fog: Color, mist: Color): Color {
    val w = white.coerceIn(0.0, 1.0)
    val grey = (round((1 - w) * 255) / 255).toFloat()
    return lerp(fog, mist, grey).copy(alpha = alpha.toFloat().coerceIn(0f, 1f))
}
