package com.beammental.app.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Phone
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
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.effects.WaveBackground
import com.beammental.app.ui.theme.BEAM_THEMES
import com.beammental.app.ui.theme.BeamColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { name = Locator.session.name().orEmpty() }

    Box(Modifier.fillMaxSize().background(BeamColors.Ink)) {
        WaveBackground(Modifier.matchParentSize())
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
        Text("Nastavenia", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = BeamColors.Mist)

        SectionLabel(Icons.Outlined.Badge, "Účet")
        Column(Modifier.fillMaxWidth().border(1.dp, BeamColors.Line, RoundedCornerShape(20.dp)).background(BeamColors.Card, RoundedCornerShape(20.dp)).padding(18.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                placeholder = { Text("Meno", color = BeamColors.Fog) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BeamColors.Sage,
                    unfocusedBorderColor = BeamColors.Line,
                    focusedContainerColor = BeamColors.Ink2,
                    unfocusedContainerColor = BeamColors.Ink2,
                    cursorColor = BeamColors.Sage,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .border(1.dp, BeamColors.Line, RoundedCornerShape(14.dp))
                    .clickable(enabled = !saving) {
                        saving = true
                        scope.launch {
                            Locator.api.saveName(name.trim())
                            saving = false
                            saved = true
                        }
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(BeamColors.Sage, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (saved) "Uložené" else if (saving) "Ukladám…" else "Uložiť",
                        color = BeamColors.SageInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                }
            }
        }

        SectionLabel(Icons.Outlined.Palette, "Motív")
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, BeamColors.Line, RoundedCornerShape(20.dp))
                .background(BeamColors.Card, RoundedCornerShape(20.dp))
                .padding(14.dp),
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
                                            .background(c, RoundedCornerShape(5.dp))
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

        SectionLabel(Icons.Outlined.SystemUpdate, "Aktualizácie")
        UpdateCard(ctx = ctx)

        SectionLabel(Icons.Outlined.Call, "Krízové linky")
        Column(Modifier.fillMaxWidth().border(1.dp, BeamColors.Line, RoundedCornerShape(20.dp)).background(BeamColors.Card, RoundedCornerShape(20.dp))) {
            listOf(
                "0800 900 900" to "Linka krízy · nonstop",
                "0800 500 500" to "IPčko · nonstop",
                "112" to "Tiesňové volanie",
            ).forEach { (number, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Phone, null, tint = BeamColors.Mist, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("$label · ", color = BeamColors.Mist, fontSize = 15.sp)
                    Text(number, color = BeamColors.Mist, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                HorizontalDivider(color = BeamColors.Line, thickness = 1.dp)
            }
        }

        SectionLabel(Icons.Outlined.Description, "Právne")
        Column(Modifier.fillMaxWidth().border(1.dp, BeamColors.Line, RoundedCornerShape(20.dp)).background(BeamColors.Card, RoundedCornerShape(20.dp))) {
            listOf(
                "ochrana-sukromia" to "Ochrana súkromia",
                "vseobecne-podmienky" to "Všeobecné podmienky",
                "zdravotny-disclaimer" to "Zdravotný disclaimer",
            ).forEach { (slug, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://beam-mental-health.vercel.app/pravne/$slug"))
                            )
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Icon(Icons.Outlined.Description, null, tint = BeamColors.Fog, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = BeamColors.Mist, fontSize = 15.sp)
                }
                HorizontalDivider(color = BeamColors.Line, thickness = 1.dp)
            }
        }

        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(BeamColors.Card, RoundedCornerShape(14.dp))
                .clickable {
                    scope.launch {
                        Locator.session.clear()
                        onLoggedOut()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Logout, null, tint = BeamColors.Fog, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Odhlásiť sa", color = BeamColors.Fog, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    }
}

@Composable
private fun SectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = BeamColors.Fog, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = BeamColors.Fog, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(0.dp))
}

/** Live "Aktualizácie" card. Subscribes to the Updater state flow, exposes
 *  manual check + force-download-over-mobile, and tells the user clearly
 *  when the new APK has a different signature (in which case the install
 *  has to happen via uninstall + reinstall, not in-place). */
@Composable
private fun UpdateCard(ctx: android.content.Context) {
    val state by Updater.state.collectAsState()
    val s = state
    val currentVer = "Beam ${BuildConfig.VERSION_NAME}"

    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, BeamColors.Line, RoundedCornerShape(20.dp))
            .background(BeamColors.Card, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.SystemUpdate, null,
                tint = BeamColors.Fog, modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text("Nainštalovaná verzia", color = BeamColors.Mist, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(currentVer, color = BeamColors.Fog, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = BeamColors.Line, thickness = 1.dp)
        Spacer(Modifier.height(12.dp))

        when (s) {
            is UpdateUi.None -> {
                Text(
                    "Kontrola nových verzií prebehne automaticky, keď si na Wi-Fi.",
                    color = BeamColors.Fog, fontSize = 13.sp, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillAction("Skontrolovať", BeamColors.Sage) {
                        Updater.checkAndMaybeStart(ctx)
                    }
                    PillAction("Stiahnuť cez mobilné dáta", BeamColors.Sage.copy(alpha = 0.14f), BeamColors.Sage) {
                        Updater.checkAndMaybeStart(ctx, allowMetered = true)
                    }
                }
            }
            is UpdateUi.Available -> {
                Text(
                    "K dispozícii je verzia ${s.info.versionName}.",
                    color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Stiahne sa sama na Wi-Fi — alebo môžeš hneď teraz.",
                    color = BeamColors.Fog, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillAction("Stiahnuť", BeamColors.Sage) { Updater.startDownload(ctx, s.info) }
                    PillAction("Hneď (mobilné dáta)", BeamColors.Sage.copy(alpha = 0.14f), BeamColors.Sage) {
                        Updater.startDownload(ctx, s.info)
                    }
                }
            }
            is UpdateUi.WaitingWifi -> {
                Text(
                    "Verzia ${s.info.versionName} — čaká sa na Wi-Fi.",
                    color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillAction("Stiahnuť cez mobilné dáta", BeamColors.Sage) {
                        Updater.startDownload(ctx, s.info)
                    }
                }
            }
            is UpdateUi.Downloading -> {
                Text(
                    "Sťahujem verziu ${s.info.versionName}…",
                    color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { s.progress },
                    color = BeamColors.Sage,
                    trackColor = BeamColors.Line,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text("${(s.progress * 100).toInt()} %", color = BeamColors.Fog, fontSize = 12.sp)
            }
            is UpdateUi.Ready -> {
                val mismatch = !s.canInstallOver
                if (mismatch) {
                    Text(
                        "Verzia ${s.info.versionName} je stiahnutá.",
                        color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Túto verziu nemožno nainštalovať ako aktualizáciu — má iný podpis. Odinštaluj starú Beam a nainštaluj novú manuálne z GitHubu.",
                        color = Color(0xFFFF9FB0), fontSize = 12.sp, lineHeight = 17.sp,
                    )
                } else {
                    Text(
                        "Verzia ${s.info.versionName} je pripravená.",
                        color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Klepni na Nainštalovať a potvrď v systéme.",
                        color = BeamColors.Fog, fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillAction(
                        if (mismatch) "Otvoriť na GitHube" else "Nainštalovať",
                        BeamColors.Sage,
                    ) {
                        if (mismatch) Updater.openInBrowser(ctx, s.info) else Updater.install(ctx, s.file)
                    }
                    if (mismatch) {
                        PillAction("Skúsiť nainštalovať", BeamColors.Sage.copy(alpha = 0.14f), BeamColors.Sage) {
                            Updater.install(ctx, s.file)
                        }
                    }
                }
            }
            is UpdateUi.Failed -> {
                Text(
                    "Sťahovanie verzie ${s.info.versionName} zlyhalo.",
                    color = Color(0xFFFF9FB0), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(s.reason, color = BeamColors.Fog, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillAction("Skúsiť znova", BeamColors.Sage) { Updater.startDownload(ctx, s.info) }
                    PillAction("Otvoriť na GitHube", BeamColors.Sage.copy(alpha = 0.14f), BeamColors.Sage) {
                        Updater.openInBrowser(ctx, s.info)
                    }
                }
            }
        }
    }
}

@Composable
private fun PillAction(
    label: String,
    bg: Color,
    fg: Color = BeamColors.SageInk,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
