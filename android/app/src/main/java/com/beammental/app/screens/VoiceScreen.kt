package com.beammental.app.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.beammental.app.ui.components.CrisisCard
import com.beammental.app.ui.effects.MeshBackground
import com.beammental.app.ui.effects.StaggeredEntrance
import com.beammental.app.ui.effects.OrbState
import com.beammental.app.ui.effects.OrbStage
import com.beammental.app.ui.effects.borderBeam
import com.beammental.app.ui.theme.BeamColors
import com.beammental.app.voice.LiveVoice
import com.beammental.app.voice.OutputMode
import com.beammental.app.voice.VoicePhase
import com.beammental.app.voice.VoiceSession
import kotlinx.coroutines.delay

/** Full-screen live voice talk with Beam (Gemini Live API). */
@Composable
fun VoiceScreen(onClose: () -> Unit, preview: VoiceSession? = null) {
    val context = LocalContext.current
    var voice by remember { mutableStateOf<VoiceSession?>(null) }
    var crisisShown by remember { mutableStateOf(false) }
    var hasMic by remember {
        mutableStateOf(
            preview != null ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMic = granted }

    val keyReady = preview != null || BuildConfig.BEAM_GOOGLE_KEY.isNotBlank()
    var muted by remember { mutableStateOf(false) }
    var outputMode by remember { mutableStateOf(OutputMode.SPEAKER) }
    var elapsedSec by remember { mutableIntStateOf(0) }

    fun startVoice() {
        muted = false
        outputMode = OutputMode.SPEAKER
        val v = preview
            ?: LiveVoice(context, BuildConfig.BEAM_GOOGLE_KEY) { crisisShown = true }
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
    BackHandler {
        v?.stop()
        onClose()
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
            StaggeredEntrance(index = 0) {
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
            }

            // stage
            StaggeredEntrance(index = 1) {
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
                        val orbState = when (phase) {
                            VoicePhase.CONNECTING -> OrbState.Thinking
                            VoicePhase.SPEAKING -> OrbState.Speaking
                            VoicePhase.LISTENING -> if (muted) OrbState.Idle else OrbState.Listening
                            else -> OrbState.Idle
                        }
                        OrbStage(
                            modifier = Modifier.size(340.dp),
                            state = orbState,
                            level = ring,
                            accent = if (phase == VoicePhase.SPEAKING) BeamColors.Accent else Color(0xFFC3CBFF),
                        )

                        Spacer(Modifier.height(30.dp))

                        val status = when (phase) {
                            VoicePhase.CONNECTING -> "Pripájam sa"
                            VoicePhase.LISTENING -> if (muted) "Mikrofón stlmený" else "Počúvam ťa"
                            VoicePhase.SPEAKING -> "Beam rozpráva"
                            VoicePhase.ENDED -> "Hovor sa skončil"
                            VoicePhase.FAILED -> v.failure ?: "Niečo sa pokazilo"
                        }
                        Text(
                            status,
                            color = BeamColors.Fog,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.8.sp,
                        )

                        Spacer(Modifier.height(26.dp))

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
            }

            // bottom bar
            StaggeredEntrance(index = 2) {
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
                            Modifier.fillMaxWidth().padding(bottom = 14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterHorizontally),
                        ) {
                            CircleControl(
                                icon = if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                label = if (muted) "Zapnúť" else "Stlmiť",
                                tint = if (muted) Color(0xFFFFC4CB) else BeamColors.Mist,
                                background = if (muted) Color(0xFFB4525E).copy(alpha = 0.20f) else BeamColors.Card,
                                borderColor = if (muted) Color(0xFFB4525E).copy(alpha = 0.55f) else BeamColors.Line,
                                onClick = {
                                    muted = !muted
                                    v.muted = muted
                                },
                            )
                            CircleControl(
                                icon = Icons.Rounded.CallEnd,
                                label = "Ukončiť",
                                size = 74.dp,
                                tint = Color(0xFFFFE1E4),
                                background = Color(0xFFB4525E).copy(alpha = 0.30f),
                                borderColor = Color(0xFFB4525E).copy(alpha = 0.72f),
                                onClick = {
                                    v.stop()
                                    onClose()
                                },
                            )
                            CircleControl(
                                icon = if (outputMode == OutputMode.SPEAKER) {
                                    Icons.AutoMirrored.Rounded.VolumeUp
                                } else {
                                    Icons.Rounded.Hearing
                                },
                                label = if (outputMode == OutputMode.SPEAKER) "Reproduktor" else "Slúchadlo",
                                onClick = {
                                    outputMode = if (outputMode == OutputMode.SPEAKER) OutputMode.EARPIECE else OutputMode.SPEAKER
                                    v.applyOutputMode()
                                },
                            )
                        }
                    }
                    if (callEnded) {
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
                                .background(BeamColors.Card, RoundedCornerShape(18.dp))
                                .border(1.dp, BeamColors.Line, RoundedCornerShape(18.dp))
                                .borderBeam(
                                    size = com.beammental.app.ui.effects.BeamSize.MD,
                                    shape = RoundedCornerShape(18.dp),
                                    strength = 0.7f,
                                )
                                .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                                    v?.stop()
                                    onClose()
                                }
                                .padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Späť na chat",
                                color = BeamColors.Mist,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                        }
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
                .background(BeamColors.Accent, RoundedCornerShape(999.dp))
                .clickable(onClick = onRequest)
                .padding(horizontal = 30.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Povoliť mikrofón", color = BeamColors.AccentInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
            .background(BeamColors.Accent, CircleShape),
    )
}

@Composable
private fun CircleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = BeamColors.Mist,
    background: Color = BeamColors.Card,
    borderColor: Color = BeamColors.Line,
    size: androidx.compose.ui.unit.Dp = 60.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, spring(dampingRatio = 0.5f))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(background, CircleShape)
                .border(1.dp, borderColor, CircleShape)
                .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                label,
                tint = tint,
                modifier = Modifier.size(if (size >= 70.dp) 28.dp else 22.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            color = BeamColors.Fog,
            fontSize = 10.sp,
            letterSpacing = 0.3.sp,
            maxLines = 1,
        )
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
            Text("!", color = BeamColors.Accent, fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
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
                .background(BeamColors.Accent, RoundedCornerShape(999.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 30.dp, vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Skúsiť znova", color = BeamColors.AccentInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}
