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
import com.beammental.app.data.Locator
import com.beammental.app.ui.effects.MascotBlob
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

    Column(
        Modifier
            .fillMaxSize()
            .background(BeamColors.Ink)
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
                    focusedBorderColor = BeamColors.Mist,
                    unfocusedBorderColor = BeamColors.Line,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = BeamColors.Mist,
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
                        .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (saved) "Uložené" else if (saving) "Ukladám…" else "Uložiť",
                        color = BeamColors.Mist, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    )
                }
            }
        }

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

@Composable
private fun SectionLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = BeamColors.Fog, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = BeamColors.Fog, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
    Spacer(Modifier.height(0.dp))
}
