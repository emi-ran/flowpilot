package com.flowpilot.app.engine

/**
 * State of foreground application and deduplication locks.
 */
data class ForegroundState(
    val currentForeground: String? = null,
    val openLocks: Set<String> = emptySet(),
)

/**
 * Pure state reducer for foreground app tracking and event dispatching.
 */
object ForegroundReducer {

    data class Transition(
        val packageName: String,
        val isForeground: Boolean,
        val timestamp: Long = 0L,
    )

    data class StepOutput(
        val state: ForegroundState,
        val openedPackage: String? = null,
        val closedPackage: String? = null,
    )

    /**
     * Reduces a sequence of transitions from a batch into a new state and determined open/close actions.
     * Transitions are sorted here because platform event batches are not trusted
     * to preserve order across OEM implementations.
     */
    fun reduceBatch(
        initialState: ForegroundState,
        transitions: List<Transition>,
    ): StepOutput {
        if (transitions.isEmpty()) {
            return StepOutput(state = initialState)
        }

        var currentForeground = initialState.currentForeground

        for (transition in transitions.sortedBy { it.timestamp }) {
            val pkg = transition.packageName
            if (transition.isForeground) {
                currentForeground = pkg
            } else {
                if (currentForeground == pkg) {
                    currentForeground = null
                }
            }
        }

        val targetForeground = currentForeground
        var finalOpened: String? = null
        var finalClosed: String? = null
        var nextLocks = initialState.openLocks

        if (initialState.currentForeground != targetForeground) {
            if (initialState.currentForeground != null) {
                finalClosed = initialState.currentForeground
                nextLocks = nextLocks - initialState.currentForeground
            }
            if (targetForeground != null) {
                if (!nextLocks.contains(targetForeground)) {
                    finalOpened = targetForeground
                    nextLocks = nextLocks + targetForeground
                }
            }
        }

        return StepOutput(
            state = ForegroundState(
                currentForeground = targetForeground,
                openLocks = nextLocks,
            ),
            openedPackage = finalOpened,
            closedPackage = finalClosed,
        )
    }
}
