package com.flowpilot.app.ui

import androidx.compose.ui.unit.Density
import com.flowpilot.app.ui.components.ActionsReorderState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActionsReorderStateTest {

    private val testDensity = Density(density = 2f, fontScale = 1f)

    @Test
    fun startDrag_singleAction_doesNotStart() {
        var reordered = false
        val state = ActionsReorderState(
            actionsCount = { 1 },
            onReorder = { _, _ -> reordered = true },
            density = testDensity,
            haptic = null,
        )

        state.startDrag(0)
        assertThat(state.draggedIndex).isNull()
        assertThat(reordered).isFalse()
    }

    @Test
    fun startDrag_multipleActions_startsSuccessfully() {
        val state = ActionsReorderState(
            actionsCount = { 2 },
            onReorder = { _, _ -> },
            density = testDensity,
            haptic = null,
        )

        state.startDrag(0)
        assertThat(state.draggedIndex).isEqualTo(0)
        assertThat(state.dragOffsetY).isEqualTo(0f)
    }

    @Test
    fun onDrag_downwards_swapsWhenThresholdExceeded() {
        var swappedFrom = -1
        var swappedTo = -1
        val items = mutableListOf("Action1", "Action2")
        val state = ActionsReorderState(
            actionsCount = { items.size },
            onReorder = { from, to ->
                swappedFrom = from
                swappedTo = to
                val item = items.removeAt(from)
                items.add(to, item)
            },
            density = testDensity,
            haptic = null,
        )
        // Set item heights
        state.itemHeights[0] = 200f
        state.itemHeights[1] = 200f

        state.startDrag(0)

        // Drag down less than threshold (threshold is ~ (200 + 20) * 0.45 = 99f)
        state.onDrag(50f)
        assertThat(swappedFrom).isEqualTo(-1)
        assertThat(state.draggedIndex).isEqualTo(0)

        // Drag down further to exceed threshold
        state.onDrag(60f) // total dragOffsetY = 110f > 99f
        assertThat(swappedFrom).isEqualTo(0)
        assertThat(swappedTo).isEqualTo(1)
        assertThat(state.draggedIndex).isEqualTo(1)
        assertThat(items).containsExactly("Action2", "Action1").inOrder()
        // Offset should have been adjusted by (200 + 20) = 220f => 110 - 220 = -110f
        assertThat(state.dragOffsetY).isEqualTo(-110f)
    }

    @Test
    fun onDrag_upwards_swapsWhenThresholdExceeded() {
        var swappedFrom = -1
        var swappedTo = -1
        val items = mutableListOf("Action1", "Action2")
        val state = ActionsReorderState(
            actionsCount = { items.size },
            onReorder = { from, to ->
                swappedFrom = from
                swappedTo = to
                val item = items.removeAt(from)
                items.add(to, item)
            },
            density = testDensity,
            haptic = null,
        )
        state.itemHeights[0] = 200f
        state.itemHeights[1] = 200f

        // Start dragging item at index 1
        state.startDrag(1)

        // Drag up past threshold
        state.onDrag(-120f)
        assertThat(swappedFrom).isEqualTo(1)
        assertThat(swappedTo).isEqualTo(0)
        assertThat(state.draggedIndex).isEqualTo(0)
        assertThat(items).containsExactly("Action2", "Action1").inOrder()
    }

    @Test
    fun endDrag_resetsState() {
        val state = ActionsReorderState(
            actionsCount = { 2 },
            onReorder = { _, _ -> },
            density = testDensity,
            haptic = null,
        )
        state.startDrag(0)
        state.onDrag(30f)
        assertThat(state.draggedIndex).isNotNull()

        state.endDrag()
        assertThat(state.draggedIndex).isNull()
        assertThat(state.dragOffsetY).isEqualTo(0f)
    }
}
