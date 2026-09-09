package com.beammental.app.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Debug builds only: a scripted [VoiceSession] so the voice screen can be
 * judged on-device (or in screenshots) without a mic, a key or a network.
 * Cycles connecting - listening - speaking with a sine "amplitude".
 */
class DebugVoice : VoiceSession {

    override var phase by mutableStateOf(VoicePhase.CONNECTING)
        private set
    override var userCaption by mutableStateOf("")
        private set
    override var modelCaption by mutableStateOf("")
        private set
    override var failure: String? by mutableStateOf(null)
        private set
    override var muted = false

    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level

    private var job: Job? = null

    override fun start() {
        job = CoroutineScope(Dispatchers.Main).launch {
            phase = VoicePhase.CONNECTING
            delay(1400)
            var turn = 0
            while (isActive) {
                val speaking = turn % 2 == 1
                phase = if (speaking) VoicePhase.SPEAKING else VoicePhase.LISTENING
                if (speaking) {
                    userCaption = ""
                    modelCaption = "Chápem. Únava vie byť signál, že toho bolo veľa. Čo ti dnes urobilo aspoň malú radosť?"
                } else {
                    modelCaption = ""
                    userCaption = "Cítim sa dnes trochu unavený, ale inak celkom dobre."
                }
                val t0 = System.nanoTime()
                while (System.nanoTime() - t0 < 4_000_000_000L && isActive) {
                    val s = (System.nanoTime() - t0) / 1_000_000_000.0
                    _level.value = if (muted && !speaking) 0f
                    else ((sin(s * 5.2) * 0.5 + 0.5) * 0.55f + 0.15f).toFloat()
                    delay(50)
                }
                _level.value = 0f
                turn++
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        _level.value = 0f
        phase = VoicePhase.ENDED
    }

    override fun applyOutputMode() = Unit
}
