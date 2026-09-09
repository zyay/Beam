package com.beammental.app.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.data.CheckinRow
import com.beammental.app.data.Locator
import com.beammental.app.ui.effects.MeshBackground
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.theme.BeamColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/* Prehľad — the user's own mental-health log: a daily mood check-in, a
 * streak, and a 30-day chart. One row per day lives on the server; the
 * screen deliberately shows only what the user gave it, nothing inferred. */

private data class Mood(val value: Int, val label: String)

private val MOODS = listOf(
    Mood(1, "Hrozné"),
    Mood(2, "Slabé"),
    Mood(3, "OK"),
    Mood(4, "Dobré"),
    Mood(5, "Skvelé"),
)

@Composable
fun PrehladScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var checkins by remember { mutableStateOf<List<CheckinRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedMood by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var savedMsg by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checkins = Locator.api.fetchCheckins()
        selectedMood = checkins.lastOrNull { it.day == todayKeyOf() }?.mood
        note = checkins.lastOrNull { it.day == todayKeyOf() }?.note.orEmpty()
        loading = false
    }

    fun save() {
        val mood = selectedMood ?: return
        saving = true
        savedMsg = false
        scope.launch {
            val ok = Locator.api.pushCheckin(mood, note)
            if (ok) {
                checkins = Locator.api.fetchCheckins()
                savedMsg = true
                kotlinx.coroutines.delay(2200)
                savedMsg = false
            }
            saving = false
        }
    }

    // streak: consecutive days ending today (or yesterday, if today is missing)
    val daySet = remember(checkins) { checkins.map { it.day }.toHashSet() }
    val streak = remember(daySet) {
        var count = 0
        val cal = Calendar.getInstance()
        if (todayKeyOf() !in daySet) cal.add(Calendar.DAY_OF_YEAR, -1)
        while (dayKeyOf(cal) in daySet) {
            count++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        count
    }
    val avg30 = remember(checkins) {
        val recent = checkins.takeLast(30)
        if (recent.isEmpty()) null else recent.sumOf { it.mood } / recent.size.toDouble()
    }

    Box(Modifier.fillMaxSize().background(BeamColors.Ink)) {
        MeshBackground(Modifier.matchParentSize(), intensity = 0.55f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack, "Späť",
                    tint = BeamColors.Fog,
                    modifier = Modifier.size(20.dp).clickable(onClick = onBack),
                )
                Spacer(Modifier.weight(1f))
                MascotBlob(modifier = Modifier.size(24.dp), blobSize = 24.dp)
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Insights, null,
                    tint = BeamColors.Sage, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Prehľad",
                    fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Mist,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Ako sa máš deň čo deň. Vidíš to len ty — údaje sú tvoje.",
                fontSize = 13.sp, lineHeight = 18.sp, color = BeamColors.Fog,
            )

            Spacer(Modifier.height(20.dp))

            /* ---- today check-in ---- */
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, BeamColors.Line, RoundedCornerShape(20.dp))
                    .background(BeamColors.Card, RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                Text(
                    "Ako sa dnes máš?",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Mist,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MOODS.forEach { m ->
                        val on = selectedMood == m.value
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(MoodPillShape)
                                .background(if (on) BeamColors.Sage else BeamColors.Sage.copy(alpha = 0.10f))
                                .border(
                                    1.dp,
                                    if (on) BeamColors.Sage else BeamColors.Line,
                                    MoodPillShape,
                                )
                                .clickable {
                                    selectedMood = m.value
                                    savedMsg = false
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                m.label,
                                fontSize = 11.sp, lineHeight = 13.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (on) BeamColors.SageInk else BeamColors.Fog,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Krátka poznámka (nepovinné)", color = BeamColors.Fog, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = BeamColors.Mist),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BeamColors.Sage,
                        unfocusedBorderColor = BeamColors.Line,
                        cursorColor = BeamColors.Sage,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { save() },
                        enabled = selectedMood != null && !saving,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BeamColors.Sage,
                            contentColor = BeamColors.SageInk,
                        ),
                    ) {
                        Text(
                            if (saving) "Ukladám…" else "Uložiť dnes",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (savedMsg) {
                        Text("Uložené.", fontSize = 13.sp, color = BeamColors.Sage)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            /* ---- stats row ---- */
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBlock(
                    modifier = Modifier.weight(1f),
                    value = if (streak > 0) "$streak" else "0",
                    label = if (streak == 1) "deň v rade" else if (streak >= 2 && streak <= 4) "dni v rade" else "dní v rade",
                )
                StatBlock(
                    modifier = Modifier.weight(1f),
                    value = avg30?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
                    label = "priemer 30 dní",
                )
                StatBlock(
                    modifier = Modifier.weight(1f),
                    value = "${checkins.size}",
                    label = "záznamov",
                )
            }

            Spacer(Modifier.height(14.dp))

            /* ---- 30-day chart ---- */
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, BeamColors.Line, RoundedCornerShape(20.dp))
                    .background(BeamColors.Card, RoundedCornerShape(20.dp))
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Posledných 30 dní",
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Mist,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("1–5", fontSize = 11.sp, color = BeamColors.Fog)
                }
                Spacer(Modifier.height(16.dp))
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = BeamColors.Sage,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    MoodChart(
                        days = last30Days(),
                        daySet = remember(checkins) { checkins.associateBy({ it.day }) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (checkins.isEmpty())
                        "Zatiaľ tu nič nie je. Prvý záznam pridá klik na náladu hore."
                    else
                        "Vyšší stĺpec = lepší deň. Sviatočné dni bez záznamu sú malé bodky.",
                    fontSize = 11.sp, lineHeight = 15.sp, color = BeamColors.Fog,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.Lock, null, tint = BeamColors.Fog, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Záznamy sú viazané na tvoj účet a nikam ich neposielame.",
                    fontSize = 11.sp, color = BeamColors.Fog,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val MoodPillShape = RoundedCornerShape(12.dp)

@Composable
private fun StatBlock(modifier: Modifier, value: String, label: String) {
    Column(
        modifier
            .border(1.dp, BeamColors.Line, RoundedCornerShape(16.dp))
            .background(BeamColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Sage)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, lineHeight = 13.sp, color = BeamColors.Fog)
    }
}

private fun dayKeyOf(cal: Calendar): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return fmt.format(cal.time)
}

private fun todayKeyOf(): String = dayKeyOf(Calendar.getInstance())

/** Last 30 day-keys ending today. */
private fun last30Days(): List<String> {
    val cal = Calendar.getInstance()
    val today = todayKeyOf()
    val out = ArrayList<String>(30)
    for (i in 29 downTo 0) {
        val c = (cal.clone() as Calendar)
        c.add(Calendar.DAY_OF_YEAR, -i)
        val key = dayKeyOf(c)
        if (key <= today) out.add(key)
    }
    return out
}

@Composable
private fun MoodChart(days: List<String>, daySet: Map<String, CheckinRow>) {
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val n = days.size.coerceAtLeast(1)
        val slot = size.width / n
        val barW = (slot * 0.55f).coerceAtMost(14.dp.toPx())
        val baseline = size.height - 2.dp.toPx()
        val maxH = size.height - 10.dp.toPx()
        days.forEachIndexed { i, day ->
            val cx = slot * i + slot / 2f
            val row = daySet[day]
            if (row == null) {
                drawCircle(
                    color = BeamColors.Line,
                    radius = 1.5.dp.toPx(),
                    center = Offset(cx, baseline - 1.5.dp.toPx()),
                )
            } else {
                val h = maxH * (row.mood / 5f)
                val color = if (day == days.last()) BeamColors.Sage else BeamColors.Sage.copy(alpha = 0.55f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(cx - barW / 2f, baseline - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 3f, barW / 3f),
                )
            }
        }
    }
}
