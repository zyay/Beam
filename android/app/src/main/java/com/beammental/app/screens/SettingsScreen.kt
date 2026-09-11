package com.beammental.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.BuildConfig
import com.beammental.app.data.Locator
import com.beammental.app.data.Updater
import com.beammental.app.data.UpdateUi
import com.beammental.app.ui.components.*
import com.beammental.app.ui.effects.MeshBackground
import com.beammental.app.ui.theme.BEAM_THEMES
import com.beammental.app.ui.theme.BeamColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onPrehlad: () -> Unit, onLegal: () -> Unit, onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { name = Locator.session.name().orEmpty() }

    Box(Modifier.fillMaxSize().background(BeamColors.Ink)) {
        MeshBackground(Modifier.matchParentSize(), intensity = 0.7f)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            BeamTopBar(
                title = "Nastavenia",
                onBack = onBack,
                mascot = "idle",
                modifier = Modifier.padding(horizontal = 10.dp),
            )

            Column(Modifier.padding(horizontal = 16.dp)) {

                BeamSectionLabel("Účet", modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
                BeamSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(18.dp),
                ) {
                    BeamTextField(
                        value = name,
                        onValueChange = { name = it; saved = false },
                        placeholder = "Meno",
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    BeamButton(
                        label = if (saved) "Uložené" else if (saving) "Ukladám…" else "Uložiť",
                        onClick = {
                            saving = true
                            scope.launch {
                                Locator.api.saveName(name.trim())
                                saving = false
                                saved = true
                            }
                        },
                        enabled = !saving,
                        busy = saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                BeamSectionLabel("Prehľad", modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
                BeamSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(16.dp),
                    onClick = onPrehlad,
                    onClickLabel = "Záznamy nálady",
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Insights, null,
                            tint = BeamColors.Accent, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Záznamy nálady",
                                color = BeamColors.Mist,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "Denný check-in a vývoj za 30 dní",
                                color = BeamColors.Fog,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                BeamSectionLabel("Motív", modifier = Modifier.padding(start = 4.dp, top = 0.dp, bottom = 8.dp))
                BeamSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    BEAM_THEMES.chunked(2).forEach { rowThemes ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowThemes.forEach { theme ->
                                val active = BeamColors.current.key == theme.key
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .background(
                                            if (active) theme.accent.copy(alpha = 0.14f) else BeamColors.Ink2,
                                            RoundedCornerShape(14.dp),
                                        )
                                        .border(
                                            1.dp,
                                            if (active) theme.accent.copy(alpha = 0.55f) else BeamColors.Line,
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable {
                                            BeamColors.apply(theme)
                                            scope.launch { Locator.session.setTheme(theme.key) }
                                        }
                                        .padding(12.dp),
                                ) {
                                    Row {
                                        theme.wave.forEach { c ->
                                            Box(
                                                Modifier
                                                    .size(16.dp)
                                                    .background(c, RoundedCornerShape(5.dp)),
                                            )
                                            Spacer(Modifier.width(5.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(9.dp))
                                    Text(
                                        theme.label,
                                        color = if (active) BeamColors.Mist else BeamColors.Fog,
                                        fontSize = 14.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                            if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                BeamSectionLabel("Aktualizácie", modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
                UpdateCard(ctx = ctx)

                BeamSectionLabel("Krízové linky", modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
                CrisisList()

                BeamSectionLabel("Právne", modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp))
                BeamSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    listOf(
                        "Ochrana súkromia",
                        "Všeobecné podmienky",
                        "Zdravotný disclaimer",
                    ).forEachIndexed { index, label ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable(onClick = onLegal)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Icon(Icons.Outlined.Description, null, tint = BeamColors.Fog, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = BeamColors.Mist, fontSize = 15.sp)
                        }
                        if (index < 2) BeamDivider(color = BeamColors.Line.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))
                BeamButton(
                    label = "Odhlásiť sa",
                    onClick = {
                        scope.launch {
                            Locator.session.clear()
                            onLoggedOut()
                        }
                    },
                    kind = BeamButtonKind.Ghost,
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun UpdateCard(ctx: android.content.Context) {
    val state by Updater.state.collectAsState()
    val s = state
    val currentVer = "Beam ${BuildConfig.VERSION_NAME}"

    if (Updater.installedFromPlay(ctx)) {
        BeamSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SystemUpdate, null, tint = BeamColors.Fog, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(12.dp))
                Text("Nainštalovaná verzia", color = BeamColors.Mist, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text(currentVer, color = BeamColors.Fog, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
            BeamDivider(color = BeamColors.Line)
            Spacer(Modifier.height(12.dp))
            Text("Aktualizácie spravuje Google Play.", color = BeamColors.Fog, fontSize = 13.sp, lineHeight = 18.sp)
        }
        return
    }

    BeamSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate, null, tint = BeamColors.Fog, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
            Text("Nainštalovaná verzia", color = BeamColors.Mist, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(currentVer, color = BeamColors.Fog, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        BeamDivider(color = BeamColors.Line)
        Spacer(Modifier.height(12.dp))

        when (s) {
            is UpdateUi.None -> {
                Text(
                    "Kontrola nových verzií prebehne automaticky, keď si na Wi-Fi.",
                    color = BeamColors.Fog, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BeamChip(label = "Skontrolovať", onClick = { Updater.checkAndMaybeStart(ctx) })
                    BeamChip(label = "Cez mobilné dáta", onClick = { Updater.checkAndMaybeStart(ctx, allowMetered = true) })
                }
            }
            is UpdateUi.Available -> {
                Text("K dispozícii je verzia ${s.info.versionName}.", color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("Stiahne sa sama na Wi-Fi — alebo môžeš hneď teraz.", color = BeamColors.Fog, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BeamChip(label = "Stiahnuť", onClick = { Updater.startDownload(ctx, s.info) }, selected = true)
                    BeamChip(label = "Hneď (mobilné dáta)", onClick = { Updater.startDownload(ctx, s.info) })
                }
            }
            is UpdateUi.WaitingWifi -> {
                Text("Verzia ${s.info.versionName} — čaká sa na Wi-Fi.", color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                BeamChip(label = "Stiahnuť cez mobilné dáta", onClick = { Updater.startDownload(ctx, s.info) })
            }
            is UpdateUi.Downloading -> {
                Text("Sťahujem verziu ${s.info.versionName}…", color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { s.progress },
                    color = BeamColors.Accent,
                    trackColor = BeamColors.Line,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text("${(s.progress * 100).toInt()} %", color = BeamColors.Fog, fontSize = 12.sp)
            }
            is UpdateUi.Ready -> {
                val mismatch = !s.canInstallOver
                if (mismatch) {
                    Text("Verzia ${s.info.versionName} je stiahnutá.", color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Túto verziu nemožno nainštalovať ako aktualizáciu — má iný podpis. Odinštaluj starú Beam a nainštaluj novú manuálne z GitHubu.",
                        color = Color(0xFFFF9FB0), fontSize = 12.sp, lineHeight = 17.sp,
                    )
                } else {
                    Text("Verzia ${s.info.versionName} je pripravená.", color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("Klepni na Nainštalovať a potvrď v systéme.", color = BeamColors.Fog, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Ak systém upozorní na neznámu aplikáciu, klepni na Detaily a vyber Inštalovať aj tak.",
                        color = BeamColors.Fog, fontSize = 12.sp, lineHeight = 17.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BeamChip(
                        label = if (mismatch) "Otvoriť na GitHube" else "Nainštalovať",
                        onClick = {
                            if (mismatch) {
                                Updater.openInBrowser(ctx, s.info)
                            } else if (!Updater.install(ctx, s.file)) {
                                Updater.requestInstallPermission(ctx)
                            }
                        },
                        selected = true,
                    )
                    if (mismatch) {
                        BeamChip(label = "Skúsiť nainštalovať", onClick = {
                            if (!Updater.install(ctx, s.file)) Updater.requestInstallPermission(ctx)
                        })
                    }
                }
            }
            is UpdateUi.Failed -> {
                Text("Sťahovanie verzie ${s.info.versionName} zlyhalo.", color = Color(0xFFFF9FB0), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(s.reason, color = BeamColors.Fog, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BeamChip(label = "Skúsiť znova", onClick = { Updater.startDownload(ctx, s.info) }, selected = true)
                    BeamChip(label = "Otvoriť na GitHube", onClick = { Updater.openInBrowser(ctx, s.info) })
                }
            }
        }
    }
}
