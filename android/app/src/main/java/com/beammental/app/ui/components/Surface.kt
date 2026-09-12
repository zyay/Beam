package com.beammental.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beammental.app.ui.effects.BeamSize
import com.beammental.app.ui.effects.borderBeam
import com.beammental.app.ui.theme.BeamColors

/**
 * The surface layer of Beam's component kit.
 *
 * libraries.dev cards are three things stacked: a #1d1d1d fill, a
 * rgba(44,47,54,0.52) hairline, and an `inset 0 0 50px rgba(255,255,255,0.02)`
 * glow that lifts the card off the #121212 page. [BeamSurface] is that stack,
 * and it is the only place in the app allowed to draw a card — every screen
 * used to hand-roll the same background+border pair, which is why the radii,
 * alphas and paddings drifted apart.
 */

/** The inset glow: a top-lit inner edge, strongest along the top where the
 *  light would come from, fading to nothing before the bottom edge. Stroked
 *  along the caller's own outline so it works at any radius. */
fun Modifier.insetGlow(shape: Shape, strength: Float = 1f): Modifier = this.drawWithContent {
    drawContent()
    val path = Path().apply {
        addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent))
    }
    drawPath(
        path,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.055f * strength),
                Color.White.copy(alpha = 0.018f * strength),
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height * 0.75f,
        ),
        style = Stroke(1.6.dp.toPx()),
    )
}

/**
 * A card.
 *
 * @param onClick when set, the surface gets a real ripple, the pressed fill and
 *   a 0.985 squeeze — never `indication = null`, which left the old cards with
 *   no touch feedback at all.
 * @param beam runs the border beam while [beamActive]; use it for "the model is
 *   working on this card" rather than as decoration.
 */
@Composable
fun BeamSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    fill: Color = BeamColors.Card,
    hairline: Color = BeamColors.Line,
    glow: Float = 1f,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    beam: Boolean = true,
    beamActive: Boolean = true,
    beamSize: BeamSize = BeamSize.MD,
    beamStrength: Float = 0.7f,
    beamPalette: List<Color>? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val squeeze by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "surfaceSqueeze",
    )
    var m = modifier
        .graphicsLayer {
            scaleX = squeeze
            scaleY = squeeze
        }
        .background(if (pressed && onClick != null) BeamColors.CardPressed else fill, shape)
        .border(1.dp, hairline, shape)
    if (glow > 0f) m = m.insetGlow(shape, glow)
    if (beam) m = m.borderBeam(
        size = beamSize,
        shape = shape,
        active = beamActive,
        strength = beamStrength,
        palette = beamPalette ?: BeamColors.BeamPalette,
    )
    if (onClick != null) {
        m = m.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
    }
    Column(m.padding(contentPadding), content = content)
}

/** Section heading: 12sp, wide-tracked, dim. libraries.dev's label colour. */
@Composable
fun BeamSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BeamColors.Fog,
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelMedium,
    )
}

/** The 1px rule under a top bar or between list rows. */
@Composable
fun BeamDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = BeamColors.Line,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color),
    )
}
