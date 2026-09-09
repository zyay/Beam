package com.beammental.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.ui.effects.OrbStage
import com.beammental.app.ui.effects.OrbState
import com.beammental.app.ui.theme.BeamColors
import androidx.compose.foundation.layout.systemBarsPadding

/**
 * Debug-only (BuildConfig.DEBUG gate in MainActivity): the four orb states on
 * ink, so the volumetric march can be judged on-device without a live call.
 */
@Composable
fun OrbLab() {
    Column(
        Modifier
            .fillMaxSize()
            .background(BeamColors.Ink)
            .systemBarsPadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Orb — lab",
            color = BeamColors.Mist,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        val states = listOf(
            OrbState.Idle to "Kľud",
            OrbState.Listening to "Počúvam",
            OrbState.Thinking to "Premýšľam",
            OrbState.Speaking to "Rozprávam",
        )
        states.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { (state, label) ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        OrbStage(
                            modifier = Modifier.size(168.dp),
                            state = state,
                            level = 0.4f,
                            accent = if (state == OrbState.Speaking) BeamColors.Sage else Color(0xFFC3CBFF),
                            orbSize = 138.dp,
                        )
                        Text(label, color = BeamColors.Fog, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    }
                }
            }
        }
    }
}
