package com.flowpilot.app.ui.tutorial

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.flowpilot.app.R
import kotlin.math.roundToInt

private const val OverlayAnimationMs = 200

private enum class TutorialStep(
    val anchorKey: String?,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    WELCOME(null, R.string.tutorial_welcome_title, R.string.tutorial_welcome_desc),
    PRESETS("home_presets", R.string.tutorial_presets_title, R.string.tutorial_presets_desc),
    CREATE("home_empty_create", R.string.tutorial_create_title, R.string.tutorial_create_desc),
    STATUS("home_status_card", R.string.tutorial_status_title, R.string.tutorial_status_desc),
    SETTINGS("home_bottom_settings", R.string.tutorial_settings_title, R.string.tutorial_settings_desc),
    DONE(null, R.string.tutorial_done_title, R.string.tutorial_done_desc),
}

private val TutorialStep.isAnchorStep: Boolean
    get() = anchorKey != null

private fun TutorialStep.hasAnchor(anchors: TutorialAnchors): Boolean {
    val key = anchorKey ?: return true
    return anchors.rects.containsKey(key)
}

/**
 * WELCOME and DONE are always navigable; anchor steps need a registered rect.
 */
private fun TutorialStep.isNavigable(anchors: TutorialAnchors): Boolean = when {
    this == TutorialStep.WELCOME || this == TutorialStep.DONE -> true
    isAnchorStep -> hasAnchor(anchors)
    else -> false
}

private fun nextVisibleIndex(from: Int, anchors: TutorialAnchors): Int {
    val steps = TutorialStep.entries
    var index = from
    while (index < steps.lastIndex) {
        index++
        if (steps[index].isNavigable(anchors)) return index
    }
    return steps.lastIndex
}

private fun previousVisibleIndex(from: Int, anchors: TutorialAnchors): Int {
    val steps = TutorialStep.entries
    var index = from
    while (index > 0) {
        index--
        if (steps[index].isNavigable(anchors)) return index
    }
    return from
}

/**
 * Full-screen interactive tour that spotlights registered anchor composables.
 * Steps whose anchor has not been laid out yet are skipped when navigating, but an
 * active step always renders its card (centered) so the overlay never fails to show.
 */
@Composable
fun TutorialOverlay(
    visible: Boolean,
    anchors: TutorialAnchors,
    onFinish: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(visible) {
        if (visible) stepIndex = TutorialStep.WELCOME.ordinal
    }

    BackHandler(enabled = visible) {
        val previous = previousVisibleIndex(stepIndex, anchors)
        if (previous == stepIndex) onFinish() else stepIndex = previous
    }

    val currentStep = TutorialStep.entries[stepIndex.coerceIn(0, TutorialStep.entries.lastIndex)]
    val anchorRect: Rect? = currentStep.anchorKey?.let { anchors.rects[it] }
    val density = LocalDensity.current
    val holeInset = with(density) { 8.dp.toPx() }
    val anchorGap = with(density) { 16.dp.toPx() }
    val edgeMargin = with(density) { 24.dp.toPx() }
    val cornerRadius = with(density) { 16.dp.toPx() }
    val strokeWidth = with(density) { 2.dp.toPx() }
    val highlightColor = MaterialTheme.colorScheme.primary

    val positioned = containerSize.width > 0 && containerSize.height > 0 && cardSize.width > 0
    val targetOffset = when {
        !positioned -> IntOffset.Zero
        anchorRect == null -> IntOffset(
            x = ((containerSize.width - cardSize.width) / 2f).roundToInt(),
            y = ((containerSize.height - cardSize.height) / 2f).roundToInt(),
        )
        else -> {
            val margin = edgeMargin.roundToInt()
            val maxX = (containerSize.width - cardSize.width - margin).coerceAtLeast(margin)
            val x = (anchorRect.center.x - cardSize.width / 2f).roundToInt().coerceIn(margin, maxX)
            val below = anchorRect.center.y < containerSize.height / 2f
            val rawY = if (below) anchorRect.bottom + anchorGap else anchorRect.top - anchorGap - cardSize.height
            val maxY = (containerSize.height - cardSize.height).coerceAtLeast(0)
            IntOffset(x, rawY.roundToInt().coerceIn(0, maxY))
        }
    }
    val cardOffset by animateIntOffsetAsState(
        targetValue = targetOffset,
        animationSpec = tween(OverlayAnimationMs),
        label = "tutorialCardOffset",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (positioned) 1f else 0f,
        animationSpec = tween(OverlayAnimationMs),
        label = "tutorialCardAlpha",
    )

    val navigableSteps = TutorialStep.entries.filter { it.isNavigable(anchors) }
    val progressTotal = navigableSteps.size.coerceAtLeast(1)
    val progressIndex = navigableSteps.indexOf(currentStep).let { if (it >= 0) it + 1 else 1 }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.zIndex(10f),
        enter = fadeIn(animationSpec = tween(OverlayAnimationMs)),
        exit = fadeOut(animationSpec = tween(OverlayAnimationMs)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        val step = TutorialStep.entries[stepIndex]
                        if (step != TutorialStep.DONE) {
                            stepIndex = nextVisibleIndex(stepIndex, anchors)
                        }
                    }
                }
                .onSizeChanged { containerSize = it },
        ) {
            val cardWidth = (maxWidth - 48.dp).coerceAtMost(360.dp).coerceAtLeast(180.dp)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                drawRect(Color.Black.copy(alpha = 0.62f))
                anchorRect?.let { rect ->
                    val hole = Rect(
                        left = rect.left - holeInset,
                        top = rect.top - holeInset,
                        right = rect.right + holeInset,
                        bottom = rect.bottom + holeInset,
                    )
                    drawRoundRect(
                        color = Color.Transparent,
                        topLeft = hole.topLeft,
                        size = Size(hole.width, hole.height),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        blendMode = BlendMode.Clear,
                    )
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = hole.topLeft,
                        size = Size(hole.width, hole.height),
                        cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                        style = Stroke(width = strokeWidth),
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .width(cardWidth)
                    .offset { cardOffset }
                    .onSizeChanged { cardSize = it }
                    .alpha(cardAlpha),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(
                        text = stringResource(currentStep.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(currentStep.descriptionRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.tutorial_progress, progressIndex, progressTotal),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (currentStep == TutorialStep.DONE) {
                        TutorialDoneActions(onFinish = onFinish, onOpenPresets = onOpenPresets)
                    } else {
                        TutorialStepActions(
                            canGoBack = previousVisibleIndex(stepIndex, anchors) != stepIndex,
                            onBack = { stepIndex = previousVisibleIndex(stepIndex, anchors) },
                            onNext = { stepIndex = nextVisibleIndex(stepIndex, anchors) },
                            onSkip = onFinish,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialStepActions(
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.tutorial_btn_skip))
        }
        Spacer(modifier = Modifier.weight(1f))
        if (canGoBack) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.tutorial_btn_back))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Button(onClick = onNext) {
            Text(stringResource(R.string.tutorial_btn_next))
        }
    }
}

@Composable
private fun TutorialDoneActions(onFinish: () -> Unit, onOpenPresets: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onFinish) {
            Text(stringResource(R.string.tutorial_btn_finish))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onOpenPresets) {
            Text(stringResource(R.string.tutorial_btn_open_presets))
        }
    }
}
