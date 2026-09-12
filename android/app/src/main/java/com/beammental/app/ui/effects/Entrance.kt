package com.beammental.app.ui.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Staggered fade + slide-up entrance. Wrap each logical block of a screen
 * with a different [index] so they cascade in sequence.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    delayPerStep: Long = 70L,
    content: @Composable () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * delayPerStep)
        appear.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 360f))
    }
    Box(
        modifier.graphicsLayer {
            alpha = appear.value
            translationY = (1f - appear.value) * 14.dp.toPx()
        },
    ) { content() }
}

/**
 * Directional slide-in for chat messages. User bubbles slide from the side they
 * sit on ([fromEnd] = true → right), assistant bubbles from the left.
 */
@Composable
fun SlideInEntrance(
    fromEnd: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 300f))
    }
    val dir = if (fromEnd) 1f else -1f
    Box(
        modifier.graphicsLayer {
            translationX = (1f - progress.value) * 20.dp.toPx() * dir
            alpha = progress.value
        },
    ) { content() }
}
