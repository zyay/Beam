package com.beammental.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beammental.app.ui.effects.EffectClock
import com.beammental.app.ui.effects.GooeyBlob
import com.beammental.app.ui.effects.GooeyLayer
import com.beammental.app.ui.effects.LiquidMotion
import com.beammental.app.ui.effects.rememberLiquidMove
import com.beammental.app.ui.theme.BeamColors

/**
 * The bottom navigation bar.
 *
 * Beam shipped without one: Prehľad and Nastavenia were reachable only from
 * header icons on the chat screen, and Prehľad could only be left by going
 * back to chat. Three destinations now share one control, and the selected
 * pill is liquid-gooey — it stretches along the axis of travel and drags a
 * droplet that bridges back into the pill as it settles.
 */

data class NavItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun BeamNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = BeamColors.Ink2,
    height: Dp = 66.dp,
) {
    Column(modifier.fillMaxWidth()) {
        BeamDivider(color = BeamColors.Line.copy(alpha = 0.6f))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(fill),
        ) {
            val cell = maxWidth / items.size
            val pillW = if (cell > 120.dp) 96.dp else cell * 0.68f
            val pillH = 36.dp
            val calm = EffectClock.reducedMotion
            val motion = rememberLiquidMove(
                target = selectedIndex.toFloat(),
                springiness = if (calm) 1f else 0.5f,
                wobble = if (calm) 0f else 0.5f,
                stretch = if (calm) 0f else 0.36f,
                trail = if (calm) 0f else 0.575f,
            )

            GooeyLayer(
                blobs = { navBlobs(motion, cell, pillW, pillH, height) },
                modifier = Modifier.matchParentSize(),
                fill = BeamColors.Button,
            ) {
                Row(Modifier.fillMaxSize()) {
                    items.forEachIndexed { index, item ->
                        NavCell(
                            item = item,
                            selected = index == selectedIndex,
                            onSelect = { onSelect(index) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The liquid: one pill at the animated index, plus a droplet trailing behind it
 * while it travels. Sizes come straight off [LiquidMotion] — stretchAlong pulls
 * the pill long, stretchAcross conserves its area, trail sizes the droplet.
 */
private fun navBlobs(
    motion: LiquidMotion,
    cell: Dp,
    pillW: Dp,
    pillH: Dp,
    barH: Dp,
): List<GooeyBlob> {
    val midY = barH / 2f
    val center = cell * (motion.position + 0.5f)
    val pill = GooeyBlob(
        x = center,
        y = midY,
        w = pillW * motion.stretchAlong,
        h = pillH * motion.stretchAcross,
    )
    val trail = motion.trail
    if (trail <= 0.001f) return listOf(pill)
    val drop = pillH * 0.6f * trail
    val back = (pillW * 0.45f + pillH * 0.35f) * trail * motion.direction
    return listOf(
        pill,
        GooeyBlob(x = center - back, y = midY, w = drop, h = drop),
    )
}

@Composable
private fun NavCell(
    item: NavItem,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val iconInk by animateColorAsState(
        targetValue = if (selected) BeamColors.Accent else BeamColors.Fog,
        animationSpec = tween(250),
        label = "navIcon",
    )
    val labelInk by animateColorAsState(
        targetValue = if (selected) BeamColors.Mist else BeamColors.Text4,
        animationSpec = tween(250),
        label = "navLabel",
    )

    Box(
        modifier.selectable(
            selected = selected,
            interactionSource = interaction,
            indication = LocalIndication.current,
            role = Role.Tab,
            onClick = onSelect,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = iconInk,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.label,
                color = labelInk,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                letterSpacing = (-0.1).sp,
                maxLines = 1,
            )
        }
    }
}
