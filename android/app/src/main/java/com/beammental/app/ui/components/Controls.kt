package com.beammental.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.beammental.app.ui.effects.BeamLoader
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.beammental.app.ui.theme.BeamColors

/**
 * The control layer of Beam's component kit.
 *
 * Three rules every control here follows, because the screens that hand-rolled
 * them broke all three:
 *
 *  1. **48dp minimum hit target.** The old header icons were 36dp, the settings
 *     back arrow 20dp, the update banner's close 16dp.
 *  2. **A real ripple.** `indication = null` was sprinkled over nine call sites,
 *     which left taps with no feedback beyond a scale animation — invisible to
 *     anyone using switch access or a keyboard.
 *  3. **A required content description.** Icon-only controls take a non-null
 *     `String`, so a missing label is a compile error rather than a silent hole
 *     in TalkBack.
 */

enum class BeamButtonKind { Primary, Neutral, Danger, Ghost }

/**
 * The one primary action per screen. [BeamButtonKind.Primary] carries the theme
 * accent, so it is the only saturated thing on an otherwise neutral surface.
 *
 * @param busy swaps the label for a spinner and swallows taps — used while the
 *   model streams, where a second submit would double-send.
 */
@Composable
fun BeamButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BeamButtonKind = BeamButtonKind.Primary,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    height: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val squeeze by animateFloatAsState(
        targetValue = if (pressed && enabled && !busy) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "buttonSqueeze",
    )
    val (fill, content, edge) = when (kind) {
        BeamButtonKind.Primary -> Triple(
            BeamColors.Accent,
            BeamColors.AccentInk,
            Color.Transparent,
        )
        BeamButtonKind.Neutral -> Triple(
            if (pressed) BeamColors.ButtonPressed else BeamColors.Button,
            BeamColors.Mist,
            BeamColors.Line,
        )
        BeamButtonKind.Danger -> Triple(
            BeamColors.Danger.copy(alpha = if (pressed) 0.24f else 0.16f),
            BeamColors.DangerInk,
            BeamColors.Danger.copy(alpha = 0.32f),
        )
        BeamButtonKind.Ghost -> Triple(
            Color.Transparent,
            BeamColors.Text3,
            BeamColors.Line,
        )
    }

    Row(
        modifier
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
            }
            .alpha(if (enabled || busy) 1f else 0.45f)
            .defaultMinSize(minHeight = height)
            .background(fill, shape)
            .then(if (edge == Color.Transparent) Modifier else Modifier.border(1.dp, edge, shape))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled && !busy,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when {
            busy -> BeamLoader(
                size = 18.dp,
                color = content,
            )
            icon != null -> {
                Icon(icon, null, tint = content, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
        }
        if (!busy) {
            Text(
                label,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Circular icon control. 48dp by default; the visual disc can be smaller via
 * [disc], but the hit area never is.
 */
@Composable
fun BeamIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    disc: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    tint: Color = BeamColors.Text3,
    fill: Color = BeamColors.Card,
    hairline: Color = BeamColors.Line,
    shape: Shape = CircleShape,
    enabled: Boolean = true,
    active: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val squeeze by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "iconSqueeze",
    )
    val edge = if (active) BeamColors.Accent.copy(alpha = 0.55f) else hairline
    val ink = if (active) BeamColors.Accent else tint

    Box(
        modifier
            .size(size)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClickLabel = contentDescription,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(disc)
                .graphicsLayer {
                    scaleX = squeeze
                    scaleY = squeeze
                }
                .alpha(if (enabled) 1f else 0.42f)
                .background(fill, shape)
                .border(1.dp, edge, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(iconSize))
        }
    }
}

/** Pill selector — mood chips, suggestion chips, filter rows. */
@Composable
fun BeamChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(999.dp),
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val squeeze by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "chipSqueeze",
    )
    val fill = when {
        selected -> BeamColors.Accent.copy(alpha = if (pressed) 0.26f else 0.18f)
        pressed -> BeamColors.CardPressed
        else -> BeamColors.Card
    }
    val edge = if (selected) BeamColors.Accent.copy(alpha = 0.55f) else BeamColors.Line
    val ink = if (selected) BeamColors.AccentSoft else BeamColors.Mist

    Row(
        modifier
            .graphicsLayer {
                scaleX = squeeze
                scaleY = squeeze
            }
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = height)
            .background(fill, shape)
            .border(1.dp, edge, shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Checkbox,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(
            label,
            color = ink,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = (-0.1).sp,
        )
    }
}

/** Every text field in the app, one colour set. */
@Composable
fun beamFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = BeamColors.Mist,
    unfocusedTextColor = BeamColors.Mist,
    disabledTextColor = BeamColors.Text4,
    focusedBorderColor = BeamColors.Accent.copy(alpha = 0.75f),
    unfocusedBorderColor = BeamColors.Line,
    disabledBorderColor = BeamColors.Line.copy(alpha = 0.5f),
    errorBorderColor = BeamColors.Danger,
    focusedContainerColor = BeamColors.Ink2,
    unfocusedContainerColor = BeamColors.Ink2,
    disabledContainerColor = BeamColors.Ink2,
    errorContainerColor = BeamColors.Ink2,
    cursorColor = BeamColors.Accent,
    errorCursorColor = BeamColors.Danger,
    focusedPlaceholderColor = BeamColors.Fog,
    unfocusedPlaceholderColor = BeamColors.Fog,
    focusedLabelColor = BeamColors.Accent,
    unfocusedLabelColor = BeamColors.Text4,
    focusedSupportingTextColor = BeamColors.Text4,
    unfocusedSupportingTextColor = BeamColors.Text4,
    errorSupportingTextColor = BeamColors.DangerInk,
)

/**
 * Text input on the shared colour set.
 *
 * @param shape 12dp by default; the chat composer passes a rounder one.
 */
@Composable
fun BeamTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = RoundedCornerShape(12.dp),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        isError = isError,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = shape,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = placeholder?.let { { Text(it, color = BeamColors.Fog) } },
        label = label?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        trailingIcon = trailing,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        colors = beamFieldColors(),
    )
}

/** Switch colours on the theme accent, so toggles belong to the palette. */
@Composable
fun beamSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = BeamColors.AccentInk,
    checkedTrackColor = BeamColors.Accent,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = BeamColors.Text4,
    uncheckedTrackColor = BeamColors.Button,
    uncheckedBorderColor = BeamColors.Line,
)

/** Small dim caption under a control or a card title. */
@Composable
fun BeamCaption(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BeamColors.Fog,
) {
    Text(text, modifier = modifier, color = color, style = MaterialTheme.typography.bodySmall)
}

/** Body copy in the secondary text colour — the step below [BeamColors.Mist]. */
@Composable
fun BeamBody(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = BeamColors.Text3,
) {
    Text(text, modifier = modifier, color = color, style = MaterialTheme.typography.bodyMedium)
}
