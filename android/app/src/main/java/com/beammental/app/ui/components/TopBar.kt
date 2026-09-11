package com.beammental.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.ui.effects.MascotBlob
import com.beammental.app.ui.theme.BeamColors

/**
 * The app's one header.
 *
 * Three screens hand-rolled it — same back arrow, same mascot, three different
 * paddings, and back arrows with a 20dp hit target. This is the shared version:
 * an optional 48dp back control, the title stack, the mascot, then caller
 * actions.
 *
 * Insets are the caller's job; every screen already pads for the status bar at
 * its own root, and doing it here too would double up.
 */
@Composable
fun BeamTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    mascot: String = "idle",
    mascotSize: Dp = 28.dp,
    divider: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                BeamIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Späť",
                    onClick = onBack,
                    size = 48.dp,
                    disc = 36.dp,
                    iconSize = 21.dp,
                    fill = Color.Transparent,
                    hairline = Color.Transparent,
                    tint = BeamColors.Text3,
                )
            } else {
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = BeamColors.Mist,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = BeamColors.Fog,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            MascotBlob(
                modifier = Modifier.size(mascotSize),
                blobSize = mascotSize,
                animation = mascot,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
        if (divider) BeamDivider(color = BeamColors.Line.copy(alpha = 0.5f))
    }
}
