package com.beammental.app.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.PowerManager
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.beammental.app.data.Api
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.sqrt

enum class VoicePhase { CONNECTING, LISTENING, SPEAKING, ENDED, FAILED }

/**
 * Live voice talk over the Gemini Live API:
 *  - dialog session: gemini-2.5-flash-native-audio-dialog family (audio in, warm Slovak voice out)
 *  - transcribe session: gemini-3.5-transcribe-live (live captions of what the user says)
 * Audio: 16 kHz mono PCM up, fixed 24 kHz mono PCM down.
 */
class LiveVoice(
    private val ctx: Context,
    private val apiKey: String,
    private val onCrisis: () -> Unit,
) {

    var phase by mutableStateOf(VoicePhase.CONNECTING)
        private set
    var userCaption by mutableStateOf("")
        private set
    var modelCaption by mutableStateOf("")
        private set
    var failure by mutableStateOf<String?>(null)
        private set

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level

    @Volatile private var active = false
    @Volatile private var flushRequested = false
    @Volatile private var crisisHandled = false

    /** Mic muted: the recorder keeps draining but nothing is sent upstream. */
    @Volatile var muted = false

    private var userTextAt = 0L
    private var newModelTurn = false

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var dialogWs: WebSocket? = null
    private var transcribeWs: WebSocket? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private val pcmQueue = LinkedBlockingQueue<ByteArray>()
    private var recThread: Thread? = null
    private var playThread: Thread? = null
    private var trackPaused = false

    // Audio session state — saved before we flip into voice-call routing,
    // restored in stop() so a normal speaker/headphone call after this still works.
    private val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var savedMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeaker: Boolean = false
    @Volatile private var audioSessionSet = false
    @Volatile private var wakeLock: PowerManager.WakeLock? = null

    private val systemInstruction =
        "Si Beam — teplý, pokojný hlasový spoločník na rozhovory o duševnej pohode. " +
            "Rozprávaš plynulú slovenčinu, krátko (jednu až tri vety), prirodzene a s teplom, ako starý priateľ. " +
            "Aktívne počúvaš, validuješ pocity a opatrne sa pýtaš jednu dopĺňujúcu otázku. " +
            "Nikdy nediagnostikuješ a nedávaš lekárske rady. " +
            "Ak človek zaznie niečo veľmi vážne — smútok, sebapoškodenie, myšlienky na smrť — spomenieš s láskou Linku krízy 0800 900 900, " +
            "IPčko 0800 500 500 a povzbudíš ho osloviť blízkeho človeka. Tichá chvíľa je v poriadku, neniekaj sa."

    fun start() {
        if (active) return
        active = true
        phase = VoicePhase.CONNECTING

        configureAudioSession()
        acquireProximityWakeLock()

        val track = buildTrack()
        this.track = track
        track.play()
        playThread = Thread { playbackLoop(track) }.apply { start() }

        val base = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

        dialogWs = client.newWebSocket(
            Request.Builder().url(base).build(),
            DialogListener(),
        )
        transcribeWs = client.newWebSocket(
            Request.Builder().url(base).build(),
            TranscribeListener(),
        )

        // watchdog: sockets that open but never finish setup must not hang CONNECTING
        Thread {
            Thread.sleep(20_000)
            if (active && phase == VoicePhase.CONNECTING) {
                active = false
                phase = VoicePhase.FAILED
                failure = "Nepodarilo sa pripojiť. Skús to znova."
                runCatching { dialogWs?.cancel() }
                runCatching { transcribeWs?.cancel() }
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        if (!active) return
        active = false
        phase = VoicePhase.ENDED
        runCatching { record?.stop() }
        runCatching { dialogWs?.close(1000, null) }
        runCatching { transcribeWs?.close(1000, null) }
        pcmQueue.clear()
        recThread?.join(500)
        playThread?.join(500)
        runCatching { record?.release() }
        runCatching { track?.pause() }
        runCatching { track?.release() }
        record = null
        track = null
        client.dispatcher.executorService.shutdown()
        _level.value = 0f
        releaseProximityWakeLock()
        restoreAudioSession()
    }

    // ---------- dialog session ----------

    private inner class DialogListener : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            ws.send(buildJsonObject {
                put("setup", buildJsonObject {
                    put("model", "models/gemini-2.5-flash-native-audio-latest")
                    put("generation_config", buildJsonObject {
                        put("response_modalities", kotlinx.serialization.json.JsonArray(
                            listOf(kotlinx.serialization.json.JsonPrimitive("AUDIO"))
                        ))
                        put("speech_config", buildJsonObject {
                            put("voice_config", buildJsonObject {
                                put("prebuilt_voice_config", buildJsonObject {
                                    put("voice_name", "Kore")
                                })
                            })
                        })
                    })
                    put("system_instruction", buildJsonObject {
                        put("parts", kotlinx.serialization.json.JsonArray(listOf(buildJsonObject {
                            put("text", systemInstruction)
                        })))
                    })
                    put("output_audio_transcription", buildJsonObject { })
                    put("input_audio_transcription", buildJsonObject { })
                })
            }.toString())
        }

        override fun onMessage(ws: WebSocket, text: String) {
            if (!active) return
            val root = runCatching {
                json.parseToJsonElement(text).jsonObject
            }.getOrNull() ?: return

            if ("setupComplete" in root) {
                if (phase == VoicePhase.CONNECTING) phase = VoicePhase.LISTENING
                startRecording(ws)
            }

            val sc = root["serverContent"]?.jsonObject ?: return

            sc["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                val data = part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
                if (data != null) {
                    if (phase != VoicePhase.SPEAKING) phase = VoicePhase.SPEAKING
                    val pcm = Base64.decode(data, Base64.NO_WRAP)
                    pcmQueue.offer(pcm)
                    if (trackPaused) resumeTrack()
                }
            }

            sc["outputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let { t ->
                if (newModelTurn) { modelCaption = ""; newModelTurn = false }
                modelCaption += t
            }

            sc["inputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull?.let { t ->
                // fallback captions when the dedicated transcribe session is down
                if (transcribeWs == null || transcribeDead) onUserTranscript(t)
            }

            if (sc["turnComplete"] != null) {
                if (phase == VoicePhase.SPEAKING) phase = VoicePhase.LISTENING
                newModelTurn = true
            }

            if (sc["interrupted"] != null) {
                flushRequested = true
                newModelTurn = true
                phase = VoicePhase.LISTENING
            }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            if (!active) return
            active = false
            phase = VoicePhase.FAILED
            failure = "Spojenie sa prerušilo. Skús to znova."
            runCatching { record?.stop() }
            releaseProximityWakeLock()
            restoreAudioSession()
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            if (active && phase != VoicePhase.ENDED) {
                active = false
                phase = VoicePhase.ENDED
                runCatching { record?.stop() }
            }
        }
    }

    // ---------- transcribe session (user captions) ----------

    @Volatile private var transcribeDead = false

    private inner class TranscribeListener : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            ws.send(buildJsonObject {
                put("setup", buildJsonObject {
                    put("model", "models/gemini-3.5-transcribe-live")
                    put("input_audio_transcription", buildJsonObject { })
                })
            }.toString())
        }

        override fun onMessage(ws: WebSocket, text: String) {
            if (!active) return
            val sc = runCatching {
                json.parseToJsonElement(text).jsonObject["serverContent"]?.jsonObject
            }.getOrNull() ?: return
            sc["inputTranscription"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                ?.let { onUserTranscript(it) }
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            transcribeDead = true // captions fall back to the dialog session
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            transcribeDead = true
        }
    }

    private fun onUserTranscript(t: String) {
        val now = System.currentTimeMillis()
        if (now - userTextAt > 3200) userCaption = "" // pause -> user started a new utterance
        userTextAt = now
        userCaption += t
        if (!crisisHandled && Api.crisisLike(userCaption)) {
            crisisHandled = true
            stop()
            onCrisis()
        }
    }

    // ---------- audio in (16 kHz mono PCM -> base64 to both sessions) ----------

    @SuppressLint("MissingPermission") // VoiceScreen checks + requests RECORD_AUDIO before start()
    private fun startRecording(dialog: WebSocket) {
        if (record != null) return
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_IN, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, 5120)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // hardware echo cancellation
            SAMPLE_IN,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize,
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            failure = "Mikrofón nie je dostupný."
            phase = VoicePhase.FAILED
            return
        }
        record = rec
        rec.startRecording()
        recThread = Thread {
            val pcm = ShortArray(FRAME_IN) // 80 ms
            val bytes = ByteArray(FRAME_IN * 2)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            while (active) {
                val n = rec.read(pcm, 0, pcm.size)
                if (n <= 0) continue
                if (muted) {
                    _level.value = 0f
                    continue
                }
                var sum = 0.0
                for (i in 0 until n) sum += pcm[i].toDouble() * pcm[i]
                _level.value = ((sqrt(sum / n) / 9000.0).coerceIn(0.0, 1.0)).toFloat()
                buf.clear()
                buf.asShortBuffer().put(pcm, 0, n)
                val b64 = Base64.encodeToString(bytes, 0, n * 2, Base64.NO_WRAP)
                val payload = buildJsonObject {
                    put("realtime_input", buildJsonObject {
                        put("media_chunks", kotlinx.serialization.json.JsonArray(listOf(buildJsonObject {
                            put("mime_type", "audio/pcm;rate=$SAMPLE_IN")
                            put("data", b64)
                        })))
                    })
                }.toString()
                dialog.send(payload)
                transcribeWs?.send(payload)
            }
            _level.value = 0f
        }.apply { start() }
    }

    // ---------- audio out (24 kHz mono PCM streaming) ----------

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_OUT, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_OUT)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuf, SAMPLE_OUT * 2))
            .build()
    }

    private fun resumeTrack() {
        runCatching {
            track?.play()
            trackPaused = false
        }
    }

    private fun playbackLoop(track: AudioTrack) {
        val slice = ByteArray(SAMPLE_OUT / 50 * 2) // 20 ms
        while (active) {
            val chunk = pcmQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
            var off = 0
            while (off < chunk.size && active) {
                if (flushRequested) {
                    flushRequested = false
                    pcmQueue.clear()
                    runCatching { track.pause(); track.flush() }
                    trackPaused = true
                    break
                }
                val len = min(chunk.size - off, slice.size)
                track.write(chunk, off, len, AudioTrack.WRITE_BLOCKING)
                off += len
            }
        }
    }

    // ---------- audio session + wake lock ----------

    private fun configureAudioSession() {
        if (audioSessionSet) return
        runCatching {
            savedMode = audioManager.mode
            savedSpeaker = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = true
            audioSessionSet = true
        }
    }

    private fun restoreAudioSession() {
        if (!audioSessionSet) return
        runCatching {
            audioManager.isSpeakerphoneOn = savedSpeaker
            audioManager.mode = savedMode
        }
        audioSessionSet = false
    }

    private fun acquireProximityWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "Beam:voice-proximity",
            ).apply { acquire(60 * 60 * 1000L) } // upper bound; released in stop()
        }
    }

    private fun releaseProximityWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    companion object {
        const val SAMPLE_IN = 16000
        const val SAMPLE_OUT = 24000
        const val FRAME_IN = 1280
    }
}
