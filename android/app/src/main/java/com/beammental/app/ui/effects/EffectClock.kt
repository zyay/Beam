package com.beammental.app.ui.effects

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext

/**
 * One frame clock for every animated effect in Beam — metal rims, the border
 * beam, the thinking orbs, the mosaic.
 *
 * Hosted once by BeamTheme, so the app pays for a single `withFrameNanos` loop
 * no matter how many effects are composed. Only [millis] is observable state;
 * every other value is derived from it, so a tick writes one slot and
 * invalidates paint only — never recomposition. Draw phases read it directly.
 */
object EffectClock {
    /** Milliseconds since the clock started. The single source of truth. */
    var millis by mutableLongStateOf(0L)
        private set

    /** Animator scale 0 ("remove animations") freezes every effect on one
     *  deterministic frame, matching the libraries.dev reduced-motion rule. */
    var reducedMotion by mutableStateOf(false)
        private set

    /** Seconds as a Double, for engine maths that must not drift. */
    val t: Double get() = millis / 1000.0

    /** Seconds as a Float, for the cheap phases (sheen drift, breathing). */
    val seconds: Float get() = millis / 1000f

    /** Chrome sweep angle in degrees. A revolution takes ~18 s: slow enough to
     *  read as a reflection drifting, fast enough to notice without staring. */
    val angle: Float get() = (millis * 0.02f) % 360f

    /** The one instant a frozen effect renders at (orbs-spec.json). */
    const val FROZEN_T = 0.6

    private var hosted = false

    @Composable
    fun Host() {
        if (hosted) return
        hosted = true

        val context = LocalContext.current
        LaunchedEffect(context) {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
            reducedMotion = scale == 0f
        }

        LaunchedEffect(Unit) {
            var lastNs = -1L
            while (true) {
                withFrameNanos { nowNs ->
                    if (lastNs < 0L) lastNs = nowNs
                    // clamp: a stalled frame must not teleport every animation
                    val dt = (nowNs - lastNs).coerceAtMost(100_000_000L)
                    lastNs = nowNs
                    if (!reducedMotion) millis += dt / 1_000_000L
                }
            }
        }
    }
}
