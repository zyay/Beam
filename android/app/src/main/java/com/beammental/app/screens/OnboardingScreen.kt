package com.beammental.app.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.data.Locator
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.effects.WaveBackground
import com.beammental.app.ui.theme.BeamColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.min

private val MOODS = listOf(
    Triple("dobre", "Dobre", Icons.Rounded.SentimentSatisfied),
    Triple("ok", "Tak-tak", Icons.Rounded.SentimentNeutral),
    Triple("tazko", "Ťažko", Icons.Rounded.Thunderstorm),
    Triple("vycerpane", "Vyčerpane", Icons.Rounded.BatteryAlert),
)
private val STRESSORS = listOf("Práca", "Škola", "Spánok", "Vzťahy", "Zdravie", "Peniaze", "Osamelosť", "Iné")
private val GOALS = listOf(
    "Menej stresu", "Lepší spánok", "Hovoriť o pocitoch",
    "Zostať pokojný/á", "Viac energie", "Rozumieť sám/samej sebe",
)
private val SLEEP = listOf(
    Triple("dobre", "Dobre", Icons.Rounded.Bedtime),
    Triple("zaspavam", "Zaspávam ťažko", Icons.Rounded.Snooze),
    Triple("budim", "Budím sa v noci", Icons.Rounded.Bed),
    Triple("malo", "Málo spánku", Icons.Rounded.BatteryAlert),
)
private val CALM = listOf("Prechádzka", "Hudba", "Rozhovor s niekým", "Písanie", "Šport", "Ticho", "Film alebo kniha", "Iné")
private val SUPPORT = listOf("Rodina", "Priatelia", "Partner/ka", "Kolegovia", "Psychológ/terapeut", "Nemám nikoho", "Ešte hľadám")
private val CADENCES = listOf("Denne", "Každý druhý deň", "Iba keď potrebujem")

private const val TOTAL_STEPS = 10

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("") }
    var stressors by remember { mutableStateOf(setOf<String>()) }
    var goals by remember { mutableStateOf(setOf<String>()) }
    var sleep by remember { mutableStateOf("") }
    var calm by remember { mutableStateOf(setOf<String>()) }
    var support by remember { mutableStateOf(setOf<String>()) }
    var cadence by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canNext = listOf(
        name.trim().length >= 2,
        mood.isNotBlank(),
        stressors.isNotEmpty(),
        goals.isNotEmpty(),
        sleep.isNotBlank(),
        true,
        true,
        cadence.isNotBlank(),
        true,
        true,
    )[step]

    fun finish() {
        if (saving) return
        saving = true
        scope.launch {
            val profile = buildJsonObject {
                put("mood", mood)
                put("stressors", JsonArray(stressors.map { JsonPrimitive(it) }))
                put("goals", JsonArray(goals.map { JsonPrimitive(it) }))
                put("sleep", sleep)
                put("calm", JsonArray(calm.map { JsonPrimitive(it) }))
                put("support", JsonArray(support.map { JsonPrimitive(it) }))
                put("cadence", cadence)
                put("time", time)
            }
            Locator.api.saveProfile(profile, name.trim())
            saving = false
            onDone()
        }
    }

    val progress by animateFloatAsState(
        (step + 1) / TOTAL_STEPS.toFloat(),
        spring(dampingRatio = 0.9f, stiffness = 120f),
    )

    Box(Modifier.fillMaxSize().background(BeamColors.Ink)) {
        WaveBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(20.dp)) {
        Spacer(Modifier.height(10.dp))
        // progress beam
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(2.dp))) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(BeamColors.Sage, RoundedCornerShape(2.dp)),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            MascotBlob(modifier = Modifier.size(24.dp), blobSize = 24.dp)
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInVertically(tween(320)) { it / 6 } + fadeIn(tween(320))) togetherWith
                        (slideOutVertically(tween(240)) { -it / 6 } + fadeOut(tween(240)))
                },
                label = "step",
            ) { s ->
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (s) {
                        0 -> StepShell("Ahoj. Ako sa voláš?", "Aby sme si nemuseli tykať medzi anonymmi.") {
                            StaggerIn(0) {
                                OutlinedTextField(
                                    value = name, onValueChange = { name = it },
                                    placeholder = { Text("Tvoje meno", color = BeamColors.Fog) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        1 -> StepShell("$name, ako sa dnes cítiš?", "Bez hodnotenia — len tak, ako to reálne je.") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                MOODS.chunked(2).forEachIndexed { row, pair ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        pair.forEachIndexed { col, (id, label, icon) ->
                                            StaggerIn(row * 2 + col, Modifier.weight(1f)) {
                                                MoodCard(icon, label, mood == id) { mood = id }
                                            }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        2 -> StepShell("Čo ťa teraz najviac zaťažuje?", "Vyber jednu alebo viac vecí.") {
                            ChipFlow(STRESSORS, stressors) { stressors = it }
                        }
                        3 -> StepShell("S čím ti to chceme skúsiť uľahčiť?", "Čo by ti tu najviac pomohlo?") {
                            ChipFlow(GOALS, goals) { goals = it }
                        }
                        4 -> StepShell("Ako ti v posledných dňoch spíš?", "Spánok o stave povie niekedy viac než nálada.") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SLEEP.chunked(2).forEachIndexed { row, pair ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        pair.forEachIndexed { col, (id, label, icon) ->
                                            StaggerIn(row * 2 + col, Modifier.weight(1f)) {
                                                MoodCard(icon, label, sleep == id) { sleep = id }
                                            }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        5 -> StepShell("Čo ti robí dobre, keď je ťažko?", "Vyber, čo ti reálne pomáha — alebo nechaj prázdne.") {
                            ChipFlow(CALM, calm) { calm = it }
                        }
                        6 -> StepShell("Na koho sa môžeš obrátiť?", "Opora nemusí byť veľká — stačí, keď existuje.") {
                            ChipFlow(SUPPORT, support) { support = it }
                        }
                        7 -> StepShell("Ako často si chceš písať?", "Len odhad — kedykoľvek to môžeš zmeniť.") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CADENCES.forEachIndexed { i, c ->
                                    StaggerIn(i) {
                                        Box(
                                            Modifier.fillMaxWidth().chipBg(cadence == c).clickable { cadence = c },
                                            contentAlignment = Alignment.Center,
                                        ) { Text(c, color = if (cadence == c) BeamColors.Mist else BeamColors.Fog, fontSize = 15.sp, modifier = Modifier.padding(vertical = 14.dp)) }
                                    }
                                }
                            }
                        }
                        8 -> StepShell("Kedy ti to najviac sedí?", "Môžeme ti ráno alebo večer pripomenúť, že tu sme.") {
                            StaggerIn(0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Snooze, null, tint = BeamColors.Fog)
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = time, onValueChange = { time = it },
                                        placeholder = { Text("napr. 20:00", color = BeamColors.Fog) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(160.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            StaggerIn(1) {
                                Text(
                                    "Radšej bez pripomienok",
                                    color = BeamColors.Fog, fontSize = 14.sp,
                                    modifier = Modifier.clickable { time = "" },
                                )
                            }
                        }
                        else -> StepShell("Hotovo, $name.", "Takto vyzerá tvoja výbava na štart.") {
                            StaggerIn(0) {
                                Row(
                                    Modifier.fillMaxWidth().border(1.dp, BeamColors.Line, RoundedCornerShape(20.dp)).background(BeamColors.Card, RoundedCornerShape(20.dp)).padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MascotBlob(modifier = Modifier.size(56.dp), blobSize = 56.dp, animation = "happy")
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text("$name · ${MOODS.firstOrNull { it.first == mood }?.second ?: ""}", color = BeamColors.Mist, fontSize = 15.sp)
                                        Spacer(Modifier.height(3.dp))
                                        Text(goals.take(3).joinToString(" · "), color = BeamColors.Fog, fontSize = 13.sp)
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            "Spánok: ${SLEEP.firstOrNull { it.first == sleep }?.second ?: "—"}" +
                                                if (calm.isNotEmpty()) " · Pomáha: ${calm.take(2).joinToString(", ")}" else "",
                                            color = BeamColors.Fog, fontSize = 13.sp,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(if (time.isBlank()) "$cadence · bez pripomienok" else "$cadence · $time", color = BeamColors.Fog, fontSize = 13.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Beam nie je zdravotnícka pomôcka ani náhrada psychológa. V kríze vždy volaj 0800 900 900.",
                                color = BeamColors.Fog, fontSize = 12.sp, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) {
                val backInteraction = remember { MutableInteractionSource() }
                val backPressed by backInteraction.collectIsPressedAsState()
                val backScale by animateFloatAsState(if (backPressed) 0.93f else 1f, spring(dampingRatio = 0.6f))
                Box(
                    Modifier
                        .weight(0.28f)
                        .height(50.dp)
                        .graphicsLayer {
                            scaleX = backScale
                            scaleY = backScale
                        }
                        .background(Color.Transparent, RoundedCornerShape(14.dp))
                        .border(1.dp, BeamColors.Line, RoundedCornerShape(14.dp))
                        .clickable(interactionSource = backInteraction, indication = null) { step-- },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = BeamColors.Fog) }
            }
            val nextInteraction = remember { MutableInteractionSource() }
            val nextPressed by nextInteraction.collectIsPressedAsState()
            val nextScale by animateFloatAsState(if (nextPressed) 0.96f else 1f, spring(dampingRatio = 0.6f))
            Box(
                Modifier
                    .weight(if (step > 0) 0.72f else 1f)
                    .height(50.dp)
                    .graphicsLayer {
                        scaleX = nextScale
                        scaleY = nextScale
                    }
                    .clickable(interactionSource = nextInteraction, indication = null, enabled = canNext && !saving) {
                        if (step < TOTAL_STEPS - 1) step++ else finish()
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (canNext) BeamColors.Sage else BeamColors.Sage.copy(alpha = 0.4f),
                            RoundedCornerShape(16.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                saving -> "Ukladám…"
                                step < TOTAL_STEPS - 1 -> "Ďalej"
                                else -> "Začať si písať"
                            },
                            color = BeamColors.SageInk, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        )
                        if (!saving) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = BeamColors.SageInk, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun StepShell(title: String, sub: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Mist, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(sub, fontSize = 14.sp, color = BeamColors.Fog, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        content()
    }
}

/** Entrance animation: content fades in and slides up, staggered by index. */
@Composable
private fun StaggerIn(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(min(index, 8) * 45L)
        appear.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 380f))
    }
    Box(
        modifier.graphicsLayer {
            alpha = appear.value
            translationY = (1f - appear.value) * 16.dp.toPx()
        },
    ) { content() }
}

@Composable
private fun MoodCard(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (active) 1.045f else 1f,
        spring(dampingRatio = 0.5f, stiffness = 480f),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(if (active) BeamColors.Sage.copy(alpha = 0.16f) else BeamColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, if (active) BeamColors.Sage.copy(alpha = 0.55f) else BeamColors.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (active) BeamColors.Mist else BeamColors.Fog, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = if (active) BeamColors.Mist else BeamColors.Fog)
    }
}

@Composable
private fun ChipFlow(options: List<String>, selected: Set<String>, onToggle: (Set<String>) -> Unit) {
    // simple two-per-row flow
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { col, opt ->
                    val active = opt in selected
                    StaggerIn(row * 2 + col, Modifier.weight(1f)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .chipBg(active)
                                .clickable {
                                    onToggle(if (active) selected - opt else selected + opt)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (active) {
                                Icon(Icons.Rounded.Check, null, tint = BeamColors.Mist, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(opt, fontSize = 14.sp, color = if (active) BeamColors.Mist else BeamColors.Fog)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Modifier.chipBg(active: Boolean): Modifier = this.background(
    if (active) BeamColors.Sage.copy(alpha = 0.14f) else BeamColors.Card,
    RoundedCornerShape(999.dp),
).border(1.dp, if (active) BeamColors.Sage.copy(alpha = 0.55f) else BeamColors.Line, RoundedCornerShape(999.dp))

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BeamColors.Sage,
    unfocusedBorderColor = BeamColors.Line,
    focusedContainerColor = BeamColors.Ink2,
    unfocusedContainerColor = BeamColors.Ink2,
    cursorColor = BeamColors.Sage,
)
