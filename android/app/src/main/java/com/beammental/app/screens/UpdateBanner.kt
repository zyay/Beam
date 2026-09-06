package com.beammental.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.data.UpdateInfo
import com.beammental.app.ui.theme.BeamColors
import java.io.File

sealed interface UpdateUi {
    data object None : UpdateUi
    data class WaitingWifi(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateUi
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUi
    data class Failed(val info: UpdateInfo) : UpdateUi
}

/** Compact "new version" banner under the chat header. */
@Composable
fun UpdateBanner(
    state: UpdateUi,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, sub) = when (state) {
        is UpdateUi.WaitingWifi -> "Nová verzia ${state.info.versionName}" to "Stiahne sa sama na Wi-Fi — alebo klepni a stiahni teraz."
        is UpdateUi.Downloading -> "Sťahujem verziu ${state.info.versionName}" to null
        is UpdateUi.Ready -> "Verzia ${state.info.versionName} je stiahnutá" to "Klepni na Nainštalovať a potvrď v systéme."
        is UpdateUi.Failed -> "Sťahovanie sa nepodarilo" to "Skús to ešte raz."
        else -> return
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .background(BeamColors.Card, RoundedCornerShape(14.dp))
            .border(1.dp, BeamColors.Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = BeamColors.Sage, modifier = Modifier.size(22.dp))
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
                Text(
                    "${(state.progress * 100).toInt()} %",
                    color = BeamColors.Fog, fontSize = 12.sp,
                )
            } else if (sub != null) {
                Spacer(Modifier.height(3.dp))
                Text(sub, color = BeamColors.Fog, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
        val action: (() -> Unit)? = when (state) {
            is UpdateUi.WaitingWifi -> { { onDownload(state.info) } }
            is UpdateUi.Failed -> { { onDownload(state.info) } }
            is UpdateUi.Ready -> { { onInstall(state.file) } }
            else -> null
        }
        val actionLabel = when (state) {
            is UpdateUi.WaitingWifi -> "Stiahnuť"
            is UpdateUi.Failed -> "Znova"
            is UpdateUi.Ready -> "Nainštalovať"
            else -> null
        }
        if (action != null && actionLabel != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .background(
                        if (state is UpdateUi.Ready) BeamColors.Sage else BeamColors.Sage.copy(alpha = 0.14f),
                        RoundedCornerShape(999.dp),
                    )
                    .clickable(onClick = action)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    actionLabel,
                    color = if (state is UpdateUi.Ready) BeamColors.SageInk else BeamColors.Sage,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
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
