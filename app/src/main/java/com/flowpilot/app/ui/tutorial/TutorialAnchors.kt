package com.flowpilot.app.ui.tutorial

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Collects the on-screen bounds of composables that the tutorial points at.
 * Rects are stored in root coordinates so the overlay can cut a hole exactly over them.
 */
class TutorialAnchors {
    val rects: SnapshotStateMap<String, Rect> = mutableStateMapOf()

    fun put(key: String, rect: Rect) {
        rects[key] = rect
    }

    fun clear() {
        rects.clear()
    }
}

val LocalTutorialAnchors = staticCompositionLocalOf<TutorialAnchors?> { null }

/**
 * Reports this composable's bounds to the closest [TutorialAnchors] provider.
 * Does nothing when no provider is installed, so it is safe to use unconditionally.
 */
fun Modifier.tutorialAnchor(key: String): Modifier = composed {
    val anchors = LocalTutorialAnchors.current
    onGloballyPositioned { coordinates ->
        anchors?.put(key, coordinates.boundsInRoot())
    }
}
