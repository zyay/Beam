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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
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
import com.beammental.app.ui.components.BeamButton
import com.beammental.app.ui.components.BeamIconButton
import com.beammental.app.ui.components.BeamTextField
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.effects.MeshBackground
import com.beammental.app.ui.theme.BeamColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.min

private val MOODS = listOf(
    Triple("dobre", "V pohode", Icons.Rounded.SentimentSatisfied),
    Triple("ok", "Tak-tak", Icons.Rounded.SentimentNeutral),
    Triple("smutne", "Smutne", Icons.Rounded.SentimentVeryDissatisfied),
    Triple("uzkostne", "Úzkostlivo", Icons.Rounded.Bolt),
    Triple("tazko", "Vystresovane", Icons.Rounded.Thunderstorm),
    Triple("vycerpane", "Vyčerpane", Icons.Rounded.BatteryAlert),
)
private val STRESSORS = listOf(
    "Práca", "Škola", "Vzťahy", "Rodina", "Zdravie", "Peniaze",
    "Spánok", "Osamelosť", "Sebakritika", "Budúcnosť", "Iné",
)
private val GOALS = listOf(
    "Zvládať stres", "Lepšie spať", "Hovoriť o pocitoch", "Zostať v pokoji",
    "Viac energie", "Porozumieť si", "Dôverovať si", "Nájsť rovnováhu",
)
private val SLEEP = listOf(
    Triple("dobre", "Spím dobre", Icons.Rounded.SentimentSatisfied),
    Triple("zaspavam", "Ťažko zaspávam", Icons.Rounded.Snooze),
    Triple("budim", "Budím sa v noci", Icons.Rounded.Thunderstorm),
    Triple("malo", "Spím málo", Icons.Rounded.BatteryAlert),
)
private val CALM = listOf(
    "Prechádzka", "Hudba", "Rozhovor", "Písanie", "Šport",
    "Dýchanie", "Tvorivá práca", "Ticho a odpočinok",
)
private val SUPPORT = listOf(
    "Rodina", "Priatelia", "Partner alebo partnerka", "Kolegovia",
    "Psychológ alebo terapeut", "Linka pomoci", "Zatiaľ nikto", "Ešte hľadám",
)
private val CADENCES = listOf("Raz denne", "Každý druhý deň", "Len keď to potrebujem")

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
    var ack by remember { mutableStateOf(false) }
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
        ack,
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
        MeshBackground(Modifier.matchParentSize(), intensity = 0.8f)
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(20.dp)) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(2.dp))) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(BeamColors.Accent, RoundedCornerShape(2.dp)),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MascotBlob(modifier = Modifier.size(24.dp), blobSize = 24.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Krok ${step + 1} z $TOTAL_STEPS",
                color = BeamColors.Fog, fontSize = 12.sp,
            )
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
                        0 -> StepShell("Ahoj.", "Teší nás. Pár otázok a Beam si prispôsobíme tebe — tvojmu dňu, tvojmu tempu, tvojmu svetu. Trvá to dve minúty.\n\nAko ti máme hovoriť?") {
                            StaggerIn(0) {
                                BeamTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    placeholder = "Tvoje meno alebo prezývka",
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            StaggerIn(1) {
                                Text(
                                    "Stačí meno alebo prezývka. To je všetko, čo teraz potrebujeme.",
                                    color = BeamColors.Fog, fontSize = 13.sp, textAlign = TextAlign.Center,
                                )
                            }
                        }
                        1 -> StepShell("Ako sa dnes máš?", "Bez filtrov a bez toho, aby si niečo hral/a. Aj ťažké dni sa počítajú.") {
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
                        2 -> StepShell("Čo ťa v poslednej dobe ťaží?", "Môže to byť práca, vzťahy, niečo vnútri — alebo úplne obyčajná vec. Môžeš vybrať aj viac oblastí. Čím úprimnejšie, tým lepšie ti Beam porozumie.") {
                            ChipFlow(STRESSORS, stressors) { stressors = it }
                        }
                        3 -> StepShell("S čím ti môžem pomôcť?", "Vyber, na čom ti záleží. Prioritu môžeš kedykoľvek zmeniť v nastaveniach — aj o polrok.") {
                            ChipFlow(GOALS, goals) { goals = it }
                        }
                        4 -> StepShell("Ako vyzerá tvoj spánok?", "Spánok prezradí o rozpoložení často viac než nálada. Ako to bolo posledné dni?") {
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
                        5 -> StepShell("Čo ti pomáha, keď je ťažko?", "Vyber spôsoby, ktoré ti reálne robia dobre. Ak zatiaľ žiadne nemáš, môžeš preskočiť — nájdeme ich spolu.") {
                            ChipFlow(CALM, calm) { calm = it }
                        }
                        6 -> StepShell("Na koho sa môžeš obrátiť?", "Opora nemusí byť veľká — stačí, keď existuje. Aj linka pomoci sa počíta.") {
                            ChipFlow(SUPPORT, support) { support = it }
                        }
                        7 -> StepShell("Ako často sa chceš zastaviť?", "Pravidelná krátka chvíľa so sebou vie urobiť viac, než občasné dlhé rozhovory. Vyber si svoj rytmus.") {
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
                        8 -> StepShell("Kedy ti pripomienka sadne najviac?", "Môžeme ti raz denne dať vedieť, že sme tu. Čas to môžeš kedykoľvek zmeniť alebo pripomienky vypnúť.") {
                            StaggerIn(0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Snooze, null, tint = BeamColors.Fog)
                                    Spacer(Modifier.width(8.dp))
                                    BeamTextField(
                                        value = time,
                                        onValueChange = { time = it },
                                        placeholder = "napr. 20:00",
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
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
                        else -> StepShell("Hotovo, $name.", "Toto je tvoj profil — nájdeš ho aj v nastaveniach a môžeš ho kedykoľvek upraviť.") {
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
                                "Beam nie je zdravotnícka pomôcka ani náhrada psychológa. V kríze vždy volaj 0800 900 900 (nonstop).",
                                color = BeamColors.Fog, fontSize = 12.sp, textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = ack,
                                    onCheckedChange = { ack = it },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = BeamColors.Accent,
                                        checkmarkColor = BeamColors.AccentInk,
                                        uncheckedColor = BeamColors.Fog,
                                    ),
                                )
                                Text(
                                    "Rozumiem. Beam mi neposkytne lekársku starostlivosť.",
                                    color = BeamColors.Fog, fontSize = 12.sp, lineHeight = 16.sp,
                                    modifier = Modifier.clickable { ack = !ack },
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) {
                BeamIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Späť",
                    onClick = { step-- },
                )
            }
            BeamButton(
                label = when {
                    saving -> "Ukladám…"
                    step < TOTAL_STEPS - 1 -> "Ďalej"
                    else -> "Začať si písať"
                },
                onClick = { if (step < TOTAL_STEPS - 1) step++ else finish() },
                enabled = canNext && !saving,
                busy = saving,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
                modifier = Modifier.weight(1f),
            )
        }
        }
    }
}

@Composable
private fun StepShell(title: String, sub: String, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Mist, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(sub, fontSize = 14.sp, color = BeamColors.Fog, textAlign = TextAlign.Center, lineHeight = 20.sp)
        Spacer(Modifier.height(26.dp))
        content()
    }
}

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
            .background(if (active) BeamColors.Accent.copy(alpha = 0.16f) else BeamColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, if (active) BeamColors.Accent.copy(alpha = 0.55f) else BeamColors.Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (active) BeamColors.Mist else BeamColors.Fog, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = if (active) BeamColors.Mist else BeamColors.Fog, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChipFlow(options: List<String>, selected: Set<String>, onToggle: (Set<String>) -> Unit) {
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
                            Text(opt, fontSize = 14.sp, color = if (active) BeamColors.Mist else BeamColors.Fog, maxLines = 2)
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
    if (active) BeamColors.Accent.copy(alpha = 0.14f) else BeamColors.Card,
    RoundedCornerShape(999.dp),
).border(1.dp, if (active) BeamColors.Accent.copy(alpha = 0.55f) else BeamColors.Line, RoundedCornerShape(999.dp))
