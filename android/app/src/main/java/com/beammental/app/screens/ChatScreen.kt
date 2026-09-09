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
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.effects.MeshBackground
import com.beammental.app.ui.theme.BeamColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CRISIS_TEXT =
    "To, čo cítiš, je veľmi vážne a nie si v tom sám/sama. Zavolaj teraz:\n\n" +
        "Linka krízy · 0800 900 900 (nonstop)\n" +
        "IPčko · 0800 500 500 (nonstop)\n" +
        "Tiesňové volanie · 112\n\n" +
        "Som len chatbot — v takejto chvíli ti musí pomôcť človek. Zavolaj, nie je to zlé rozhodnutie."

@Composable
fun ChatScreen(onPrehlad: () -> Unit, onSettings: () -> Unit) {
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
    var voiceOpen by remember { mutableStateOf(false) }
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
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        // header — translucent glass bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(BeamColors.Ink.copy(alpha = 0.55f))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MascotBlob(
                modifier = Modifier.size(32.dp), blobSize = 32.dp,
                animation = when {
                    busy -> "thinking"
                    input.isNotBlank() -> "listening"
                    else -> "idle"
                },
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Beam",
                    fontWeight = FontWeight.SemiBold,
                    color = BeamColors.Mist,
                    fontSize = 15.sp,
                    letterSpacing = (-0.2).sp,
                )
                Text(
                    display,
                    color = BeamColors.Fog,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            HeaderIcon(
                icon = { Icon(Icons.Rounded.Mic, "Hlasový režim", tint = BeamColors.Sage, modifier = Modifier.size(20.dp)) },
                onClick = { voiceOpen = true },
            )
            Spacer(Modifier.width(8.dp))
            HeaderIcon(
                icon = { Icon(Icons.Outlined.Insights, "Prehľad", tint = BeamColors.Sage, modifier = Modifier.size(20.dp)) },
                onClick = onPrehlad,
            )
            Spacer(Modifier.width(8.dp))
            HeaderIcon(
                icon = { Icon(Icons.Outlined.Add, "Nový rozhovor", tint = BeamColors.Fog, modifier = Modifier.size(20.dp)) },
                onClick = {
                    if (busy) return@HeaderIcon
                    messages = emptyList(); error = null
                    scope.launch { persist() }
                },
            )
            Spacer(Modifier.width(8.dp))
            HeaderIcon(
                icon = { Icon(Icons.Outlined.Settings, "Nastavenia", tint = BeamColors.Fog, modifier = Modifier.size(20.dp)) },
                onClick = onSettings,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BeamColors.Line.copy(alpha = 0.5f)),
        )

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
                        val chipInteraction = remember { MutableInteractionSource() }
                        val chipPressed by chipInteraction.collectIsPressedAsState()
                        val chipScale by animateFloatAsState(
                            if (chipPressed) 0.96f else 1f,
                            spring(dampingRatio = 0.55f, stiffness = 380f),
                            label = "chipScale",
                        )
                        Text(
                            hint,
                            color = BeamColors.Mist,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = chipScale
                                    scaleY = chipScale
                                }
                                .background(BeamColors.Card.copy(alpha = 0.7f), RoundedCornerShape(999.dp))
                                .border(1.dp, BeamColors.Line.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                                .clickable(interactionSource = chipInteraction, indication = null) { input = hint }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
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
                                color = BeamColors.SageInk,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                modifier = Modifier
                                    .fillMaxWidth(0.84f)
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                BeamColors.Sage,
                                                BeamColors.Sage.copy(alpha = 0.92f),
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
                                        .background(BeamColors.Sage, CircleShape),
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
                                                .background(BeamColors.Sage),
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
                                            indication = null,
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
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Čo máš na srdci?", color = BeamColors.Fog.copy(alpha = 0.85f)) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeamColors.Sage.copy(alpha = 0.7f),
                        unfocusedBorderColor = BeamColors.Line.copy(alpha = 0.6f),
                        focusedContainerColor = BeamColors.Ink2.copy(alpha = 0.7f),
                        unfocusedContainerColor = BeamColors.Ink2.copy(alpha = 0.7f),
                        cursorColor = BeamColors.Sage,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                val enabled = !busy && input.isNotBlank()
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val sendScale by animateFloatAsState(
                    if (pressed) 0.88f else 1f,
                    spring(dampingRatio = 0.5f, stiffness = 380f),
                    label = "sendScale",
                )
                Box(
                    Modifier
                        .size(46.dp)
                        .alpha(if (enabled) 1f else 0.45f)
                        .graphicsLayer {
                            scaleX = sendScale
                            scaleY = sendScale
                        }
                        .background(BeamColors.Sage, CircleShape)
                        .clickable(interactionSource = interaction, indication = null, enabled = enabled) { send() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Send,
                        "Poslať",
                        tint = BeamColors.SageInk,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
        }

        if (voiceOpen) {
            VoiceScreen(onClose = { voiceOpen = false })
        }
    }
}

@Composable
private fun HeaderIcon(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.85f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "headerIconScale",
    )
    Box(
        Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(BeamColors.Card.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, BeamColors.Line.copy(alpha = 0.6f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
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

@Composable
fun CrisisCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BeamColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, BeamColors.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, tint = Color.White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Krízová pomoc", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        listOf(
            "0800 900 900" to "Linka krízy · nonstop",
            "0800 500 500" to "IPčko · nonstop",
            "112" to "Tiesňové volanie",
        ).forEach { (number, label) ->
            val ctx = LocalContext.current
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                    .clickable {
                        ctx.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$number"))
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Phone, null, tint = BeamColors.Mist, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text("$label · ", color = BeamColors.Mist, fontSize = 14.sp)
                Text(number, color = BeamColors.Mist, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(7.dp))
        }
        Text(
            "Som len chatbot — v kríze ti musí pomôcť človek. Zavolaj, nie je to zlé rozhodnutie.",
            color = BeamColors.Fog, fontSize = 12.sp,
        )
    }
}
