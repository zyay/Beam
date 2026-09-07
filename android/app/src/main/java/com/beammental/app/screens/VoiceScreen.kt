package com.beammental.app.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.beammental.app.BuildConfig
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.effects.MeshBackground
import com.beammental.app.ui.theme.BeamColors
import com.beammental.app.voice.LiveVoice
import com.beammental.app.voice.OutputMode
import com.beammental.app.voice.VoicePhase
import kotlinx.coroutines.delay

/** Full-screen live voice talk with Beam (Gemini Live API). */
@Composable
fun VoiceScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    var voice by remember { mutableStateOf<LiveVoice?>(null) }
    var crisisShown by remember { mutableStateOf(false) }
    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMic = granted }

    val keyReady = BuildConfig.BEAM_GOOGLE_KEY.isNotBlank()
    var muted by remember { mutableStateOf(false) }
    var outputMode by remember { mutableStateOf(OutputMode.SPEAKER) }
    var elapsedSec by remember { mutableIntStateOf(0) }

    fun startVoice() {
        muted = false
        outputMode = OutputMode.SPEAKER
        val v = LiveVoice(context, BuildConfig.BEAM_GOOGLE_KEY) { crisisShown = true }
        voice = v
        v.applyOutputMode()
        v.start()
    }

    LaunchedEffect(hasMic) {
        if (hasMic && voice == null && !crisisShown && keyReady) startVoice()
    }

    val v = voice
    if (v != null) {
        DisposableEffect(v) {
            onDispose { v.stop() }
        }
    }
    val callEnded = v == null || v.phase == VoicePhase.ENDED || v.phase == VoicePhase.FAILED

    // session timer, resets whenever a (new) call becomes active
    LaunchedEffect(!callEnded) {
        if (!callEnded) {
            elapsedSec = 0
            while (true) {
                delay(1000)
                elapsedSec++
            }
        }
    }

    Box(Modifier.fillMaxSize().background(BeamColors.Ink)) {
        val phase = v?.phase
        MeshBackground(
            modifier = Modifier.matchParentSize(),
            intensity = when (phase) {
                com.beammental.app.voice.VoicePhase.SPEAKING -> 1f
                com.beammental.app.voice.VoicePhase.LISTENING -> 0.85f
                com.beammental.app.voice.VoicePhase.CONNECTING -> 0.7f
                else -> 0.5f
            },
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
        ) {
            // header
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hlasový rozhovor", fontWeight = FontWeight.SemiBold, color = BeamColors.Mist)
                if (!callEnded) {
                    Spacer(Modifier.width(8.dp))
                    LiveDot()
                }
                Spacer(Modifier.weight(1f))
                if (!callEnded && elapsedSec > 0) {
                    Text(
                        "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60),
                        color = BeamColors.Fog, fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Icon(
                    Icons.Rounded.Close, "Zavrieť",
                    tint = BeamColors.Fog,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable {
                            v?.stop()
                            onClose()
                        },
                )
            }

            // stage
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (!keyReady) {
                    MissingKeyNotice()
                } else if (!hasMic) {
                    MicPermissionNotice { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                } else if (v != null && v.phase == VoicePhase.FAILED) {
                    FailedNotice(
                        reason = v.failure ?: "Niečo sa pokazilo.",
                        onRetry = { voice = null; startVoice() },
                    )
                } else if (v != null) {
                    val phase = v.phase
                    val level by v.level.collectAsState()
                    val ring by animateFloatAsState(level, spring(dampingRatio = 0.5f, stiffness = 700f))
                    val rot = rememberInfiniteTransition(label = "ring-rotation")
                    val ringAngle by rot.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
                        label = "ring-angle",
                    )

                    // aura + dot ring + mascot
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(320.dp),
                    ) {
                        LevelDotRing(
                            level = ring,
                            angle = ringAngle,
                            phaseBoost = if (phase == VoicePhase.SPEAKING) 0.7f else 1f,
                            modifier = Modifier.matchParentSize(),
                        )
                        Box(
                            Modifier
                                .size(232.dp)
                                .graphicsLayer {
                                    alpha = 0.07f + ring * 0.20f
                                    scaleX = 1f + ring * 0.22f
                                    scaleY = 1f + ring * 0.22f
                                }
                                .background(BeamColors.Sage.copy(alpha = 0.35f), CircleShape)
                        )
                        val pulse = rememberInfiniteTransition(label = "aura")
                        val breathe by pulse.animateFloat(
                            initialValue = 0.92f,
                            targetValue = 1.06f,
                            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
                            label = "breathe",
                        )
                        Box(
                            Modifier
                                .size(172.dp)
                                .graphicsLayer {
                                    val s = if (phase == VoicePhase.SPEAKING) breathe else 1f + ring * 0.10f
                                    scaleX = s
                                    scaleY = s
                                    alpha = 0.12f + ring * 0.14f
                                }
                                .background(BeamColors.Sage.copy(alpha = 0.45f), CircleShape)
                        )
                        MascotBlob(
                            modifier = Modifier.size(132.dp),
                            blobSize = 132.dp,
                            animation = when (phase) {
                                VoicePhase.CONNECTING -> "thinking"
                                VoicePhase.SPEAKING -> "happy"
                                VoicePhase.LISTENING -> "listening"
                                else -> "idle"
                            },
                        )
                    }

                    Spacer(Modifier.height(34.dp))

                    val status = when (phase) {
                        VoicePhase.CONNECTING -> "Pripájam sa…"
                        VoicePhase.LISTENING -> if (muted) "Mikrofón je stlmený." else "Počúvam ťa…"
                        VoicePhase.SPEAKING -> "Beam rozpráva…"
                        VoicePhase.ENDED -> "Hovor sa skončil."
                        VoicePhase.FAILED -> v.failure ?: "Niečo sa pokazilo."
                    }
                    Text(status, color = BeamColors.Fog, fontSize = 15.sp)

                    Spacer(Modifier.height(26.dp))

                    // captions
                    AnimatedVisibility(visible = v.userCaption.isNotBlank(), enter = fadeIn(tween(240)), exit = fadeOut(tween(400))) {
                        Text(
                            "„${v.userCaption.trim()}",
                            color = BeamColors.Fog,
                            fontStyle = FontStyle.Italic,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth(0.86f),
                        )
                    }
                    AnimatedVisibility(visible = v.modelCaption.isNotBlank(), enter = fadeIn(tween(240)), exit = fadeOut(tween(600))) {
                        Text(
                            v.modelCaption.trim(),
                            color = BeamColors.Mist,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(0.86f),
                        )
                    }
                }
            }

            // bottom bar
            Column(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (v != null && v.phase == VoicePhase.ENDED) {
                    Text(
                        "Hlasový hovor sa ukončil. Môžeš to skúsiť znova, alebo sa vrátiť späť na písanie.",
                        color = BeamColors.Fog, fontSize = 12.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    )
                }
                if (v != null && !callEnded) {
                    if (elapsedSec >= 840) {
                        Text(
                            "Hovor je už dlhý — ak sa spojenie preruší, klepni na „Skúsiť znova“.",
                            color = BeamColors.Fog, fontSize = 12.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    ) {
                        val micInteraction = remember { MutableInteractionSource() }
                        val micPressed by micInteraction.collectIsPressedAsState()
                        val micScale by animateFloatAsState(if (micPressed) 0.94f else 1f, spring(dampingRatio = 0.55f))
                        Row(
                            Modifier
                                .graphicsLayer { scaleX = micScale; scaleY = micScale }
                                .background(
                                    if (muted) Color(0xFFB4525E).copy(alpha = 0.18f) else BeamColors.Card,
                                    RoundedCornerShape(999.dp),
                                )
                                .border(
                                    1.dp,
                                    if (muted) Color(0xFFB4525E).copy(alpha = 0.5f) else BeamColors.Line,
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable(interactionSource = micInteraction, indication = null) {
                                    muted = !muted
                                    v.muted = muted
                                }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                null,
                                tint = if (muted) Color(0xFFFFC4CB) else BeamColors.Sage,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (muted) "Zapnúť" else "Stlmiť",
                                color = if (muted) Color(0xFFFFC4CB) else BeamColors.Mist,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        val outInteraction = remember { MutableInteractionSource() }
                        val outPressed by outInteraction.collectIsPressedAsState()
                        val outScale by animateFloatAsState(if (outPressed) 0.94f else 1f, spring(dampingRatio = 0.55f))
                        Row(
                            Modifier
                                .graphicsLayer { scaleX = outScale; scaleY = outScale }
                                .background(BeamColors.Card, RoundedCornerShape(999.dp))
                                .border(1.dp, BeamColors.Line, RoundedCornerShape(999.dp))
                                .clickable(interactionSource = outInteraction, indication = null) {
                                    outputMode = if (outputMode == OutputMode.SPEAKER) OutputMode.EARPIECE else OutputMode.SPEAKER
                                    v.applyOutputMode()
                                }
                                .padding(horizontal = 18.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (outputMode == OutputMode.SPEAKER) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.Hearing,
                                null,
                                tint = BeamColors.Sage,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (outputMode == OutputMode.SPEAKER) "Reproduktor" else "Slúchadlo",
                                color = BeamColors.Mist,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                val ended = callEnded
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .background(
                            if (ended) BeamColors.Card else Color(0xFFB4525E).copy(alpha = 0.22f),
                            RoundedCornerShape(18.dp),
                        )
                        .clickable(interactionSource = interaction, indication = null) {
                            v?.stop()
                            onClose()
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (ended) "Späť na chat" else "Ukončiť hovor",
                        color = if (ended) BeamColors.Fog else Color(0xFFFFC4CB),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Hovor sa prenáša online (Google). Nahrávka sa po hovore neukladá.",
                    color = BeamColors.Fog.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // crisis overlay — fixed protocol, same as chat
        if (crisisShown) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(BeamColors.Ink.copy(alpha = 0.94f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CrisisCard()
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Zavrieť",
                        color = BeamColors.Fog,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .background(BeamColors.Card, RoundedCornerShape(999.dp))
                            .clickable {
                                crisisShown = false
                                onClose()
                            }
                            .padding(horizontal = 26.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MicPermissionNotice(onRequest: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Mic, null, tint = BeamColors.Fog, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Hlasový režim potrebuje mikrofón.",
            color = BeamColors.Mist, fontWeight = FontWeight.Medium, fontSize = 16.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Beam ťa bude počúvať a rozprávať sa s tebou. Nikto iný hovor nepočuje.",
            color = BeamColors.Fog, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .background(BeamColors.Sage, RoundedCornerShape(999.dp))
                .clickable(onClick = onRequest)
                .padding(horizontal = 30.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Povoliť mikrofón", color = BeamColors.SageInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

@Composable
private fun MissingKeyNotice() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Mic, null, tint = BeamColors.Fog, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Hlasový režim nie je v tomto builde nastavený.",
            color = BeamColors.Mist, fontSize = 15.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Nainštaluj si APK z oficiálneho vydania.",
            color = BeamColors.Fog, fontSize = 13.sp,
        )
    }
}

@Composable
private fun LiveDot() {
    val transition = rememberInfiniteTransition(label = "live-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "live-alpha",
    )
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(BeamColors.Sage, CircleShape),
    )
}

@Composable
private fun LevelDotRing(
    level: Float,
    angle: Float,
    phaseBoost: Float,
    modifier: Modifier = Modifier,
) {
    val n = 36
    val baseRadiusFraction = 0.46f
    val baseDot = 4.dp
    val maxDot = 9.dp
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension * baseRadiusFraction
        val rotRad = Math.toRadians(angle.toDouble())
        for (i in 0 until n) {
            val theta = (i.toDouble() / n) * 2.0 * Math.PI + rotRad
            val x = cx + radius * kotlin.math.cos(theta).toFloat()
            val y = cy + radius * kotlin.math.sin(theta).toFloat()
            val phase = i.toFloat() / n * (Math.PI * 2.0).toFloat() * 2f
            val wave = 0.5f + 0.5f * kotlin.math.sin(phase)
            val boost = (level * phaseBoost) * (0.4f + 0.6f * wave)
            val sz = baseDot.toPx() + (maxDot.toPx() - baseDot.toPx()) * boost
            val a = (0.18f + 0.72f * boost).coerceIn(0f, 1f)
            drawCircle(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = a),
                radius = sz / 2f,
                center = Offset(x, y),
            )
        }
    }
}

@Composable
private fun FailedNotice(reason: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .background(BeamColors.Card, CircleShape)
                .border(1.dp, BeamColors.Line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("!", color = BeamColors.Sage, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Hovor sa skončil.",
            color = BeamColors.Mist, fontWeight = FontWeight.Medium, fontSize = 17.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            reason,
            color = BeamColors.Fog, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.78f),
        )
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .background(BeamColors.Sage, RoundedCornerShape(999.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 30.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Skúsiť znova", color = BeamColors.SageInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}
