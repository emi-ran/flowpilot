package com.flowpilot.app.engine

import com.flowpilot.app.data.model.TriggerEvent

/** Ringer mode as exposed by AudioManager; only the three user-selectable modes exist. */
enum class RingerState {
    NORMAL,
    VIBRATE,
    SILENT;

    /** The trigger a rule uses to wait for this ringer state. */
    val triggerEvent: TriggerEvent
        get() = when (this) {
            NORMAL -> TriggerEvent.RINGER_NORMAL
            VIBRATE -> TriggerEvent.RINGER_VIBRATE
            SILENT -> TriggerEvent.RINGER_SILENT
        }

    companion object {
        // Mirrors AudioManager.RINGER_MODE_* so the mapping stays a pure function over ints.
        private const val MODE_SILENT = 0
        private const val MODE_VIBRATE = 1
        private const val MODE_NORMAL = 2

        /** Unknown or out-of-range values yield null so callers ignore them. */
        fun fromAudioMode(mode: Int): RingerState? = when (mode) {
            MODE_NORMAL -> NORMAL
            MODE_VIBRATE -> VIBRATE
            MODE_SILENT -> SILENT
            else -> null
        }
    }
}

/** Do Not Disturb as a simple on/off; any restricting interruption filter counts as ON. */
enum class DndState {
    ON,
    OFF;

    val triggerEvent: TriggerEvent
        get() = when (this) {
            ON -> TriggerEvent.DND_ENABLED
            OFF -> TriggerEvent.DND_DISABLED
        }

    companion object {
        // Mirrors NotificationManager.INTERRUPTION_FILTER_*.
        private const val FILTER_UNKNOWN = 0
        private const val FILTER_ALL = 1
        private const val FILTER_PRIORITY = 2
        private const val FILTER_NONE = 3
        private const val FILTER_ALARMS = 4

        /** UNKNOWN (0) and unrecognised values yield null so callers ignore them. */
        fun fromInterruptionFilter(filter: Int): DndState? = when (filter) {
            FILTER_ALL -> OFF
            FILTER_PRIORITY, FILTER_NONE, FILTER_ALARMS -> ON
            FILTER_UNKNOWN -> null
            else -> null
        }
    }
}

/**
 * Process-wide marker for state changes FlowPilot itself is about to make (ringer, Do Not Disturb,
 * stream volume). The sound-state trackers ignore changes observed shortly afterwards, which stops a
 * rule such as "ringer silent -> set ringer silent / DND on" from re-triggering itself.
 *
 * Holds one timestamp only; no state, package or payload is stored.
 */
object OwnSoundChangeGuard {
    /** Broadcasts are delivered within milliseconds; the window only has to cover delivery lag. */
    const val WINDOW_MS = 2_000L

    @Volatile
    private var lastOwnChangeMs: Long? = null

    /** Call immediately before and after an own action writes sound state. */
    fun markOwnChange(nowMs: Long = System.currentTimeMillis()) {
        lastOwnChangeMs = nowMs
    }

    fun lastMark(): Long? = lastOwnChangeMs

    /** Test hook; clears the marker. */
    fun reset() {
        lastOwnChangeMs = null
    }

    /** True when [nowMs] falls inside the suppression window that follows [lastOwnChangeMs]. */
    fun isSuppressed(nowMs: Long, lastOwnChangeMs: Long?, windowMs: Long = WINDOW_MS): Boolean =
        lastOwnChangeMs != null && nowMs - lastOwnChangeMs < windowMs
}

/**
 * Decides which observed state values count as a real transition. Pure and single-threaded by
 * contract (callers synchronise); unit-tested without Android.
 *
 * - The first value, or a seeded one, only establishes the baseline and never fires.
 * - Repeated identical values never fire (sticky broadcasts, volume-only ringer broadcasts).
 * - Null (unknown) values are ignored and leave the baseline untouched.
 * - A change inside the own-change window still updates the baseline but does not fire, so the
 *   follow-up user change is compared against the true current state.
 */
class StateTransitionReducer<S : Any> {
    private var current: S? = null

    /** Sets the baseline without emitting. */
    fun seed(state: S?) {
        if (state != null) current = state
    }

    fun reset() {
        current = null
    }

    /** Returns the new state when it should fire a trigger, otherwise null. */
    fun observe(state: S?, nowMs: Long, lastOwnChangeMs: Long?): S? {
        if (state == null) return null
        val previous = current
        current = state
        if (previous == null || previous == state) return null
        if (OwnSoundChangeGuard.isSuppressed(nowMs, lastOwnChangeMs)) return null
        return state
    }
}
