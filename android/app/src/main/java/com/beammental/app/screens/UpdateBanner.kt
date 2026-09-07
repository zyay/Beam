package com.beammental.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.data.UpdateInfo
import com.beammental.app.data.UpdateUi
import com.beammental.app.ui.theme.BeamColors
import java.io.File

/** Compact "new version" banner under the chat header. */
@Composable
fun UpdateBanner(
    state: UpdateUi,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onOpenInBrowser: (UpdateInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val info: UpdateInfo = when (state) {
        is UpdateUi.Available -> state.info
        is UpdateUi.WaitingWifi -> state.info
        is UpdateUi.Downloading -> state.info
        is UpdateUi.Ready -> state.info
        is UpdateUi.Failed -> state.info
        UpdateUi.None -> return
    }
    val isReady = state is UpdateUi.Ready
    val canOver = isReady && state.canInstallOver
    val mismatch = isReady && !state.canInstallOver

    val (title, sub) = when (state) {
        is UpdateUi.Available -> "Nová verzia ${state.info.versionName}" to "Klepni na Stiahnuť a nainštalujeme."
        is UpdateUi.WaitingWifi -> "Nová verzia ${state.info.versionName}" to "Stiahne sa sama na Wi-Fi — alebo klepni a stiahni teraz."
        is UpdateUi.Downloading -> "Sťahujem verziu ${state.info.versionName}" to null
        is UpdateUi.Ready -> if (mismatch) "Verzia ${state.info.versionName} je stiahnutá" to
            "Túto verziu nemožno nainštalovať cez aktualizáciu — má iný podpis. Odinštaluj starú Beam a nainštaluj novú manuálne."
        else "Verzia ${state.info.versionName} je stiahnutá" to "Klepni na Nainštalovať a potvrď v systéme."
        is UpdateUi.Failed -> "Sťahovanie ${state.info.versionName} zlyhalo" to state.reason
        else -> "" to null
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .background(
                if (mismatch) Color(0xFFFF6E82).copy(alpha = 0.06f) else BeamColors.Card,
                RoundedCornerShape(14.dp),
            )
            .border(
                1.dp,
                if (mismatch) Color(0xFFFF6E82).copy(alpha = 0.35f) else BeamColors.Line,
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (mismatch) Icons.Rounded.Warning else Icons.Rounded.SystemUpdate,
            null,
            tint = if (mismatch) Color(0xFFFFC4CB) else BeamColors.Sage,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = BeamColors.Mist, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (state is UpdateUi.Downloading) {
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    color = BeamColors.Sage,
                    trackColor = BeamColors.Line,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                Spacer(Modifier.height(5.dp))
                Text("${(state.progress * 100).toInt()} %", color = BeamColors.Fog, fontSize = 12.sp)
            } else if (sub != null) {
                Spacer(Modifier.height(3.dp))
                Text(sub, color = BeamColors.Fog, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        // primary action
        val primary: Pair<String, () -> Unit>? = when (state) {
            is UpdateUi.Available -> "Stiahnuť" to { onDownload(state.info) }
            is UpdateUi.WaitingWifi -> "Stiahnuť" to { onDownload(state.info) }
            is UpdateUi.Failed -> "Znova" to { onDownload(state.info) }
            is UpdateUi.Ready -> if (mismatch) "Otvoriť" to { onOpenInBrowser(state.info) }
            else "Nainštalovať" to { onInstall(state.file) }
            is UpdateUi.Downloading -> null
            UpdateUi.None -> null
        }
        if (primary != null) {
            Spacer(Modifier.width(10.dp))
            val (label, action) = primary
            val sageBg = !mismatch && (state is UpdateUi.Ready || state is UpdateUi.Available)
            Box(
                Modifier
                    .background(
                        if (sageBg) BeamColors.Sage else BeamColors.Sage.copy(alpha = 0.14f),
                        RoundedCornerShape(999.dp),
                    )
                    .clickable(onClick = action)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    color = if (sageBg) BeamColors.SageInk else BeamColors.Sage,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        // when ready + signature mismatch, also offer the install attempt first
        if (mismatch) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .background(BeamColors.Sage.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                    .clickable { onInstall(state.file) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Skúsiť nainštalovať", color = BeamColors.Sage, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Rounded.Close, "Zavrieť",
            tint = BeamColors.Fog,
            modifier = Modifier.size(16.dp).clickable(onClick = onDismiss),
        )
    }
}
