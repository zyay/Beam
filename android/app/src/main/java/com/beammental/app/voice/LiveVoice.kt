package com.beammental.app.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.PowerManager
import android.util.Base64
import android.util.Log
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
enum class OutputMode { SPEAKER, EARPIECE }

/** What the voice screen needs from a call. [LiveVoice] is the real thing;
 *  a scripted session can drive the same UI in debug builds. */
interface VoiceSession {
    val phase: VoicePhase
    val level: StateFlow<Float>
    val userCaption: String
    val modelCaption: String
    val failure: String?
    var muted: Boolean
    fun start()
    fun stop()
    fun applyOutputMode()
}

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
) : VoiceSession {

    override var phase by mutableStateOf(VoicePhase.CONNECTING)
        private set
    override var userCaption by mutableStateOf("")
        private set
    override var modelCaption by mutableStateOf("")
        private set
    override var failure by mutableStateOf<String?>(null)
        private set

    private val _level = MutableStateFlow(0f)
    override val level: StateFlow<Float> = _level

    @Volatile private var active = false
    @Volatile private var flushRequested = false
    @Volatile private var crisisHandled = false

    /** Mic muted: the recorder keeps draining but nothing is sent upstream. */
    @Volatile override var muted = false

    /** Default = speakerphone so the user can hear Beam hands-free. Earpiece
     *  is for when they want privacy (caller-style routing). Flip with
     *  [setOutputMode] — safe to call mid-call. */
    @Volatile var outputMode: OutputMode = OutputMode.SPEAKER

    /** Last time we sent or received any WebSocket message, ms. Used to
     *  decide whether to keep the dialog socket alive with a ping. */
    @Volatile private var lastWsTrafficAt = 0L
    /** Set once we've sent a single setupComplete — controls auto-retry. */
    @Volatile private var setupCompleted = false
    /** True if this session is being auto-retried after a transient failure. */
    @Volatile private var isRetry = false

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
    private var focusRequest: AudioFocusRequest? = null

    private val systemInstruction =
        "Si Beam — teplý, pokojný hlasový spoločník na rozhovory o duševnej pohode. " +
            "Rozprávaš plynulú slovenčinu, krátko (jednu až tri vety), prirodzene a s teplom, ako starý priateľ. " +
            "Aktívne počúvaš, validuješ pocity a opatrne sa pýtaš jednu dopĺňujúcu otázku. " +
            "Nikdy nediagnostikuješ a nedávaš lekárske rady. " +
            "Ak človek zaznie niečo veľmi vážne — smútok, sebapoškodenie, myšlienky na smrť — spomenieš s láskou Linku krízy 0800 900 900, " +
            "IPčko 0800 500 500 a povzbudíš ho osloviť blízkeho človeka. Tichá chvíľa je v poriadku, neniekaj sa.\n\n" +
            "Keď sa hovor práve pripojil, vždy pozdrav prvá — srdečne, ale stručne. Napríklad: " +
            "„Ahoj, som Beam. Rád ťa počujem. Ako sa dnes máš?“"

    override fun start() {
        if (active) return
        active = true
        phase = VoicePhase.CONNECTING
        setupCompleted = false
        lastWsTrafficAt = System.currentTimeMillis()

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

        // 1 · setup watchdog: if the dialog socket opens but never completes
        // setup, the call should not hang in CONNECTING forever. 30s is
        // generous — a working setup usually completes in <8s.
        Thread {
            Thread.sleep(30_000)
            if (active && phase == VoicePhase.CONNECTING) {
                if (!setupCompleted && !isRetry) {
                    // transient blip: tear down and try once more, in-place
                    isRetry = true
                    Log.w(TAG, "setup watchdog fired — auto-retrying dialog socket")
                    runCatching { dialogWs?.cancel() }
                    runCatching { transcribeWs?.cancel() }
                    Thread.sleep(800)
                    if (!active) return@Thread
                    phase = VoicePhase.CONNECTING
                    setupCompleted = false
                    lastWsTrafficAt = System.currentTimeMillis()
                    dialogWs = client.newWebSocket(
                        Request.Builder().url(base).build(),
                        DialogListener(),
                    )
                    transcribeWs = client.newWebSocket(
                        Request.Builder().url(base).build(),
                        TranscribeListener(),
                    )
                } else if (!setupCompleted) {
                    // already retried, give up
                    active = false
                    phase = VoicePhase.FAILED
                    failure = "Nepodarilo sa pripojiť. Skús to znova."
                    runCatching { dialogWs?.cancel() }
                    runCatching { transcribeWs?.cancel() }
                }
            }
        }.apply { isDaemon = true }.start()

        // 2 · keepalive: long pauses with no audio or transcript chunks let
        // intermediate proxies / NATs kill the socket. Send a tiny empty
        // audio chunk every 25s whenever nothing else has moved. We only do
        // this before setup completes; once we're in LISTENING, the model
        // is talking back and forth anyway, so the keepalive is implicit.
        Thread {
            while (active) {
                Thread.sleep(5_000)
                if (!active) break
                val since = System.currentTimeMillis() - lastWsTrafficAt
                if (since > 25_000 && setupCompleted) {
                    val ws = dialogWs
                    if (ws != null) {
                        // 100 ms of digital silence — keeps the stream warm
                        // without confusing the model.
                        val silence = ShortArray(1600) // 100 ms @ 16 kHz
                        val bytes = ByteArray(silence.size * 2)
                        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                        b.asShortBuffer().put(silence)
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val ok = ws.send(
                            buildJsonObject {
                                put("realtime_input", buildJsonObject {
                                    put("media_chunks", kotlinx.serialization.json.JsonArray(listOf(buildJsonObject {
                                        put("mime_type", "audio/pcm;rate=$SAMPLE_IN")
                                        put("data", b64)
                                    })))
                                })
                            }.toString()
                        )
                        if (ok) lastWsTrafficAt = System.currentTimeMillis()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    override fun stop() {
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
                        // No thinking — the model defaults to ~10-13s of "thoughts"
                        // before the first audio chunk, which feels broken to the
                        // user. The model still speaks thoughtfully without it.
                        put("thinking_config", buildJsonObject {
                            put("thinking_budget", 0)
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
            Log.d(TAG, "dialog ws open, setup sent")
        }

        override fun onMessage(ws: WebSocket, text: String) {
            if (!active) return
            val root = runCatching {
                json.parseToJsonElement(text).jsonObject
            }.getOrNull() ?: return

            if ("setupComplete" in root) {
                Log.d(TAG, "dialog setupComplete")
                setupCompleted = true
                if (phase == VoicePhase.CONNECTING) phase = VoicePhase.LISTENING
                // Make Beam speak first — prompt a short user turn so the model
                // opens with a warm Slovak greeting instead of waiting in silence
                // for the user to find their voice.
                triggerGreeting(ws)
                startRecording(ws)
            }
            lastWsTrafficAt = System.currentTimeMillis()

            val sc = root["serverContent"]?.jsonObject ?: return

            sc["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                val data = part.jsonObject["inlineData"]?.jsonObject?.get("data")?.jsonPrimitive?.contentOrNull
                if (data != null) {
                    if (phase != VoicePhase.SPEAKING) {
                        Log.d(TAG, "first model audio chunk: ${data.length} b64 chars")
                        phase = VoicePhase.SPEAKING
                    }
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
            lastWsTrafficAt = System.currentTimeMillis()
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

    // ---------- greeting trigger ----------

    /** After setup, push a tiny user "Ahoj" turn so the model opens the call
     *  with its greeting instead of waiting in silence. The system instruction
     *  is what makes the greeting warm and Slovak. */
    private fun triggerGreeting(dialog: WebSocket) {
        val payload = "{" +
            "\"client_content\":{" +
            "\"turns\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Ahoj\"}]}]," +
            "\"turn_complete\":true" +
            "}}"
        runCatching {
            val ok = dialog.send(payload)
            if (ok) lastWsTrafficAt = System.currentTimeMillis()
            Log.d(TAG, "greeting triggered")
        }.onFailure { Log.w(TAG, "triggerGreeting failed", it) }
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
                lastWsTrafficAt = System.currentTimeMillis()
            }
            _level.value = 0f
        }.apply { start() }
    }

    // ---------- audio out (24 kHz mono PCM streaming) ----------

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_OUT, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
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
            // 4-second buffer — gives the speaker time to drain even when the
            // model spits chunks faster than 1× realtime.
            .setBufferSizeInBytes(maxOf(minBuf, SAMPLE_OUT * 2 * 4))
            .build()
        // Force max volume on the track itself. The system stream volume can
        // be 0 from a previous session (e.g. user muted music); this guarantees
        // we are audible without us fiddling with the user's volume setting.
        runCatching { track.setVolume(AudioTrack.getMaxVolume()) }
        Log.d(TAG, "AudioTrack built: ${SAMPLE_OUT}Hz mono, max volume")
        return track
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
            // explicit audio focus (the AudioTrack on USAGE_VOICE_COMMUNICATION
            // usually gets it automatically, but on some OEM builds the system
            // mutes the stream unless we ask nicely)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val fr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { }
                    .build()
                focusRequest = fr
                audioManager.requestAudioFocus(fr)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                )
            }
            audioSessionSet = true
            Log.d(TAG, "audio session: mode=COMM, speaker=true, focus=requested")
        }.onFailure { Log.w(TAG, "configureAudioSession failed", it) }
    }

    private fun restoreAudioSession() {
        if (!audioSessionSet) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            focusRequest = null
            audioManager.isSpeakerphoneOn = savedSpeaker
            audioManager.mode = savedMode
        }.onFailure { Log.w(TAG, "restoreAudioSession failed", it) }
        audioSessionSet = false
    }

    /** Flip between speakerphone and earpiece routing without tearing down the
     *  call. Speaker = hands-free; Earpiece = private (caller-style). */
    override fun applyOutputMode() {
        runCatching {
            audioManager.isSpeakerphoneOn = (outputMode == OutputMode.SPEAKER)
            Log.d(TAG, "output mode -> $outputMode (speakerphone=${audioManager.isSpeakerphoneOn})")
        }.onFailure { Log.w(TAG, "applyOutputMode failed", it) }
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
        private const val TAG = "Beam"
    }
}
