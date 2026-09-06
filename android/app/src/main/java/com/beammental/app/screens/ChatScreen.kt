package com.beammental.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.data.Api
import com.beammental.app.data.ChatMessage
import com.beammental.app.data.Locator
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.theme.BeamColors
import kotlinx.coroutines.launch

private const val CRISIS_TEXT =
    "To, čo cítiš, je veľmi vážne a nie si v tom sám/sama. Zavolaj teraz:\n\n" +
        "Linka krízy · 0800 900 900 (nonstop)\n" +
        "IPčko · 0800 500 500 (nonstop)\n" +
        "Tiesňové volanie · 112\n\n" +
        "Som len chatbot — v takejto chvíli ti musí pomôcť človek. Zavolaj, nie je to zlé rozhodnutie."

@Composable
fun ChatScreen(onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        name = Locator.session.name() ?: "kamarát"
        messages = Locator.session.chatHistory()
    }

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
        busy = true
        scope.launch {
            val history = messages
                .filter { it.second == "user" || it.second == "assistant" }
                .takeLast(12)
                .map { (text, role) -> ChatMessage(role, text) }
            val res = Locator.api.chat(history)
            busy = false
            if (res.text != null) {
                messages = messages + (res.text to "assistant")
            } else {
                error = res.error
            }
            persist()
        }
    }

    Column(Modifier.fillMaxSize().background(BeamColors.Ink)) {
        // header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MascotBlob(modifier = Modifier.size(28.dp), blobSize = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text("Beam", fontWeight = FontWeight.SemiBold, color = BeamColors.Mist)
            Text(" · $name", color = BeamColors.Fog, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Outlined.Add, "Nový rozhovor",
                tint = BeamColors.Fog,
                modifier = Modifier.size(22.dp).clickable(enabled = !busy) {
                    messages = emptyList(); error = null
                    scope.launch { persist() }
                },
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                Icons.Outlined.Settings, "Nastavenia",
                tint = BeamColors.Fog,
                modifier = Modifier.size(22.dp).clickable(onClick = onSettings),
            )
        }
        HorizontalDivider(color = BeamColors.Line, thickness = 1.dp)

        // messages
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty() && !busy) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    MascotBlob(modifier = Modifier.size(96.dp), blobSize = 96.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("Ahoj, $name.", color = BeamColors.Mist, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Som Beam — tvoje kľudné miesto na rozhovor.\nČo ťa dnes trápi, alebo čo ťa teší?",
                        color = BeamColors.Fog, fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages) { (text, role) ->
                    when (role) {
                        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(
                                text,
                                color = BeamColors.Mist,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .background(BeamColors.Sage.copy(alpha = 0.16f), RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                            )
                        }
                        "assistant:crisis" -> CrisisCard()
                        else -> Text(
                            text,
                            color = BeamColors.Mist,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.fillMaxWidth(0.9f),
                        )
                    }
                }
                if (busy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MascotBlob(modifier = Modifier.size(36.dp), blobSize = 36.dp, thinking = true)
                            Spacer(Modifier.width(10.dp))
                            Text("Premýšľam…", color = BeamColors.Fog, fontSize = 14.sp)
                        }
                    }
                }
                error?.let { e ->
                    item {
                        Text(
                            e,
                            color = Color(0xFFFF9FB0), fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFF6E82).copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        )
                    }
                }
            }

            // auto-scroll to bottom
            LaunchedEffect(messages.size, busy) {
                if (messages.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }

        // composer
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, BeamColors.Ink)),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Čo máš na srdci?", color = BeamColors.Fog) },
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BeamColors.Sage,
                    unfocusedBorderColor = BeamColors.Line,
                    focusedContainerColor = Color(0xFF1D1913),
                    unfocusedContainerColor = Color(0xFF1D1913),
                    cursorColor = BeamColors.Sage,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            val enabled = !busy && input.isNotBlank()
            Box(
                Modifier
                    .size(48.dp)
                    .alpha(if (enabled) 1f else 0.5f)
                    .background(
                        BeamColors.Sage,
                        CircleShape,
                    )
                    .clickable(enabled = enabled) { send() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Send, "Poslať", tint = BeamColors.SageInk, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CrisisCard() {
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
