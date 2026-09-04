package com.flowpilot.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Manages drag-and-drop state for reordering actions in an automation rule.
 */
class ActionsReorderState(
    var actionsCount: () -> Int,
    var onReorder: (Int, Int) -> Unit,
    var density: Density,
    var haptic: HapticFeedback?,
) {
    var draggedIndex by mutableStateOf<Int?>(null)
    var dragOffsetY by mutableFloatStateOf(0f)
    val itemHeights = mutableStateMapOf<Int, Float>()

    fun startDrag(index: Int) {
        if (actionsCount() <= 1) return
        draggedIndex = index
        dragOffsetY = 0f
        haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDrag(dragAmountY: Float) {
        val currentIndex = draggedIndex ?: return
        val count = actionsCount()
        if (count <= 1) return
        dragOffsetY += dragAmountY

        val spacingPx = with(density) { 10.dp.toPx() }

        // Dragging down towards currentIndex + 1
        if (currentIndex < count - 1) {
            val nextHeight = itemHeights[currentIndex + 1] ?: 300f
            val threshold = (nextHeight + spacingPx) * 0.45f
            if (dragOffsetY > threshold) {
                val offsetAdjustment = nextHeight + spacingPx
                dragOffsetY -= offsetAdjustment
                val newIndex = currentIndex + 1
                draggedIndex = newIndex
                onReorder(currentIndex, newIndex)
                haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        // Dragging up towards currentIndex - 1
        if (currentIndex > 0) {
            val prevHeight = itemHeights[currentIndex - 1] ?: 300f
            val threshold = -((prevHeight + spacingPx) * 0.45f)
            if (dragOffsetY < threshold) {
                val offsetAdjustment = prevHeight + spacingPx
                dragOffsetY += offsetAdjustment
                val newIndex = currentIndex - 1
                draggedIndex = newIndex
                onReorder(currentIndex, newIndex)
                haptic?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    fun endDrag() {
        draggedIndex = null
        dragOffsetY = 0f
    }
}

@Composable
fun rememberActionsReorderState(
    actionsCount: () -> Int,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
): ActionsReorderState {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val state = remember {
        ActionsReorderState(
            actionsCount = actionsCount,
            onReorder = onReorder,
            density = density,
            haptic = haptic,
        )
    }
    state.actionsCount = actionsCount
    state.onReorder = onReorder
    state.density = density
    state.haptic = haptic
    return state
}

@Composable
fun Modifier.actionDragTarget(
    index: Int,
    reorderState: ActionsReorderState,
): Modifier {
    val isDragging = reorderState.draggedIndex == index
    val translationY = if (isDragging) reorderState.dragOffsetY else 0f
    return this
        .onGloballyPositioned { coords ->
            reorderState.itemHeights[index] = coords.size.height.toFloat()
        }
        .zIndex(if (isDragging) 10f else 0f)
        .graphicsLayer {
            this.translationY = translationY
            if (isDragging) {
                scaleX = 1.02f
                scaleY = 1.02f
            }
        }
        .shadow(
            elevation = if (isDragging) 12.dp else 0.dp,
            shape = RoundedCornerShape(18.dp),
        )
        .then(
            if (isDragging) {
                Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(18.dp)
                )
            } else Modifier
        )
}

fun Modifier.actionDragHandle(
    getIndex: () -> Int,
    reorderState: ActionsReorderState,
): Modifier {
    if (reorderState.actionsCount() <= 1) return this
    return this.pointerInput(reorderState) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                reorderState.startDrag(getIndex())
            },
            onDragEnd = {
                reorderState.endDrag()
            },
            onDragCancel = {
                reorderState.endDrag()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                reorderState.onDrag(dragAmount.y)
            }
        )
    }
}
