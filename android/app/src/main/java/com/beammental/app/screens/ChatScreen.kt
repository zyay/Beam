package com.beammental.app.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.data.Api
import com.beammental.app.data.ChatMessage
import com.beammental.app.data.Locator
import com.beammental.app.data.UpdateUi
import com.beammental.app.data.Updater
import com.beammental.app.ui.components.BeamChip
import com.beammental.app.ui.components.BeamIconButton
import com.beammental.app.ui.components.BeamTextField
import com.beammental.app.ui.components.BeamTopBar
import com.beammental.app.ui.components.CRISIS_TEXT
import com.beammental.app.ui.components.CrisisCard
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.effects.MeshBackground
import com.beammental.app.ui.theme.BeamColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(onVoice: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    var name by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUser by remember { mutableStateOf<String?>(null) }
    val updateUi by Updater.state.collectAsState()
    var bannerDismissed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val caretTransition = rememberInfiniteTransition(label = "caret")
    val caretAlpha by caretTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "caret-alpha",
    )

    // auto-update: check the latest release, download over Wi-Fi, notify.
    // Updater owns its own scope now, so navigating away from chat does not
    // cancel the in-flight download.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Updater.notificationsEnabled(context)) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Play installs must not self-update — Play policy; store handles it.
        if (!Updater.installedFromPlay(context)) {
            Updater.checkAndMaybeStart(context)
        }
    }

    // Wi-Fi arriving later continues the auto-update
    DisposableEffect(Unit) {
        val cb = Updater.observeWifi(context) {
            // only kick off if we were waiting for Wi-Fi
            if (!Updater.installedFromPlay(context) && Updater.state.value is UpdateUi.WaitingWifi) {
                val st = Updater.state.value as UpdateUi.WaitingWifi
                Updater.startDownload(context, st.info)
            }
        }
        onDispose { Updater.unregisterWifi(context, cb) }
    }

    LaunchedEffect(Unit) {
        name = Locator.session.name() ?: ""
        messages = Locator.session.chatHistory()
        // fresh install: pull the name from the profile so we don't show the fallback
        if (name.isBlank()) {
            Locator.api.fetchProfileName()?.let {
                Locator.session.setName(it)
                name = it
            }
        }
    }
    val display = name.ifBlank { "kamarát" }

    suspend fun persist() {
        Locator.session.saveChatHistory(messages)
    }

    fun send() {
        val content = input.trim()
        if (content.isBlank() || busy) return
        input = ""
        error = null

        // client-side crisis guard: fixed protocol, no round-trip
        if (Api.crisisLike(content)) {
            messages = messages + (content to "user") + (CRISIS_TEXT to "assistant:crisis")
            scope.launch { persist() }
            return
        }

        messages = messages + (content to "user")
        lastUser = content
        val history = messages
            .filter { it.second == "user" || it.second == "assistant" }
            .takeLast(12)
            .map { (text, role) -> ChatMessage(role, text) }
        // empty assistant placeholder grows as SSE deltas arrive
        messages = messages + ("" to "assistant")
        busy = true
        streaming = false
        scope.launch {
            val res = Locator.api.chatStream(history) { delta ->
                streaming = true
                val cur = messages.toMutableList()
                val i = cur.lastIndex
                if (i >= 0) cur[i] = (cur[i].first + delta) to cur[i].second
                messages = cur
            }
            busy = false
            if (res.text != null && res.text.isNotBlank()) {
                val role = if (res.crisis) "assistant:crisis" else "assistant"
                messages = messages.dropLast(1) + (res.text to role)
            } else {
                messages = messages.dropLast(1)
                error = res.error
            }
            persist()
        }
    }

    Box(Modifier.fillMaxSize().background(BeamColors.Ink)) {
        MeshBackground(
            modifier = Modifier.matchParentSize(),
            intensity = if (busy || streaming || input.isNotBlank()) 1f else 0f,
        )
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BeamTopBar(
            title = "Beam",
            subtitle = display,
            mascot = when {
                busy -> "thinking"
                input.isNotBlank() -> "listening"
                else -> "idle"
            },
            mascotSize = 32.dp,
        ) {
            BeamIconButton(
                icon = Icons.Rounded.Mic,
                contentDescription = "Hlasový režim",
                onClick = onVoice,
                tint = BeamColors.Accent,
            )
            BeamIconButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Nový rozhovor",
                onClick = {
                    if (busy) return@BeamIconButton
                    messages = emptyList(); error = null
                    scope.launch { persist() }
                },
            )
        }

        if (updateUi !is UpdateUi.None && !bannerDismissed) {
            UpdateBanner(
                state = updateUi,
                onDownload = { info -> Updater.startDownload(context, info) },
                onInstall = { file ->
                    if (!Updater.install(context, file)) Updater.requestInstallPermission(context)
                },
                onOpenInBrowser = { info -> Updater.openInBrowser(context, info) },
                onDismiss = { bannerDismissed = true },
            )
        }

        // messages
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty() && !busy) {
                var greeted by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(2400)
                    greeted = false
                }
                val appear = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 180f))
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    MascotBlob(
                        modifier = Modifier
                            .size(120.dp)
                            .graphicsLayer {
                                scaleX = appear.value
                                scaleY = appear.value
                                alpha = appear.value
                            },
                        blobSize = 120.dp,
                        animation = if (greeted) "happy" else "idle",
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Ahoj, $display.",
                        color = BeamColors.Mist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp,
                        letterSpacing = (-0.4).sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Som Beam — tvoje kľudné miesto na rozhovor.\nČo ťa dnes trápi, alebo čo ťa teší?",
                        color = BeamColors.Fog,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(34.dp))
                    listOf(
                        "Dnes mám ťažký deň",
                        "Neviem, čo cítim",
                        "Chcem sa podeliť o radosť",
                    ).forEach { hint ->
                        BeamChip(
                            label = hint,
                            onClick = { input = hint },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(messages, key = { i, _ -> i }) { index, (text, role) ->
                    when (role) {
                        "user" -> Row(
                            Modifier.fillMaxWidth().animateItem(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text,
                                color = BeamColors.AccentInk,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                modifier = Modifier
                                    .fillMaxWidth(0.84f)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                BeamColors.Accent,
                                                BeamColors.Accent.copy(alpha = 0.92f),
                                            ),
                                        ),
                                        RoundedCornerShape(
                                            topStart = 18.dp,
                                            topEnd = 18.dp,
                                            bottomStart = 18.dp,
                                            bottomEnd = 5.dp,
                                        ),
                                    )
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                            )
                        }
                        "assistant:crisis" -> Box(Modifier.fillMaxWidth().animateItem()) { CrisisCard() }
                        else -> {
                            // blinking caret while the answer streams in
                            val showCaret = busy && index == messages.lastIndex
                            Row(
                                Modifier.fillMaxWidth().animateItem(),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    Modifier
                                        .padding(top = 9.dp, end = 9.dp)
                                        .size(6.dp)
                                        .background(BeamColors.Accent, CircleShape),
                                )
                                Column(Modifier.fillMaxWidth(0.92f)) {
                                    Text(
                                        text,
                                        color = BeamColors.Mist,
                                        fontSize = 15.sp,
                                        lineHeight = 23.sp,
                                        letterSpacing = 0.1.sp,
                                    )
                                    if (showCaret) {
                                        Box(
                                            Modifier
                                                .padding(top = 3.dp)
                                                .size(width = 7.dp, height = 16.dp)
                                                .alpha(caretAlpha)
                                                .background(BeamColors.Accent),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (busy && !streaming) {
                    item(key = "busy") {
                        Row(Modifier.animateItem(), verticalAlignment = Alignment.CenterVertically) {
                            MascotBlob(modifier = Modifier.size(36.dp), blobSize = 36.dp, thinking = true)
                            Spacer(Modifier.width(10.dp))
                            ThinkingLabel()
                        }
                    }
                }
                error?.let { e ->
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFF6E82).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(e, color = Color(0xFFFF9FB0), fontSize = 14.sp)
                            if (lastUser != null) {
                                Spacer(Modifier.height(8.dp))
                                val retryInteraction = remember { MutableInteractionSource() }
                                val retryPressed by retryInteraction.collectIsPressedAsState()
                                val retryScale by animateFloatAsState(
                                    if (retryPressed) 0.95f else 1f,
                                    spring(dampingRatio = 0.55f),
                                )
                                Row(
                                    Modifier
                                        .graphicsLayer {
                                            scaleX = retryScale
                                            scaleY = retryScale
                                        }
                                        .background(Color(0xFFFF6E82).copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                                        .clickable(
                                            interactionSource = retryInteraction,
                                            indication = LocalIndication.current,
                                            enabled = !busy,
                                        ) {
                                            error = null
                                            input = lastUser ?: ""
                                            send()
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Skúsiť znova",
                                        color = Color(0xFFFFC4CB),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // auto-scroll: follow the growing stream instantly, settle with animation
            LaunchedEffect(messages.size, messages.lastOrNull()?.first?.length, busy) {
                if (messages.isEmpty()) return@LaunchedEffect
                val last = listState.layoutInfo.totalItemsCount - 1
                if (last < 0) return@LaunchedEffect
                if (busy) listState.scrollToItem(last) else listState.animateScrollToItem(last)
            }
        }

        // composer — glass bar floating above the keyboard
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BeamColors.Ink.copy(alpha = 0f),
                            BeamColors.Ink.copy(alpha = 0.7f),
                            BeamColors.Ink.copy(alpha = 0.85f),
                        ),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                BeamTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "Čo máš na srdci?",
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                BeamIconButton(
                    icon = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Poslať",
                    onClick = { send() },
                    enabled = !busy && input.isNotBlank(),
                    fill = BeamColors.Accent,
                    hairline = Color.Transparent,
                    tint = BeamColors.AccentInk,
                    iconSize = 19.dp,
                    disc = 40.dp,
                    size = 46.dp,
                )
            }
        }
        }
    }
}

@Composable
private fun ThinkingLabel() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Premýšľam", color = BeamColors.Fog, fontSize = 14.sp)
        repeat(3) { i ->
            val a by transition.animateFloat(
                initialValue = 0.15f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(520, delayMillis = i * 170, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Text(".", color = BeamColors.Fog, fontSize = 15.sp, modifier = Modifier.alpha(a))
        }
    }
}
