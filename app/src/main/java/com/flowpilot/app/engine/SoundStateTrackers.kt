package com.flowpilot.app.engine

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Runtime receiver for ringer-mode changes. Registered only while an enabled rule needs it
 * (see [TriggerListenerDemand.needsRinger]) and unregistered otherwise. Decisions live in
 * [StateTransitionReducer]; this class only adapts Android callbacks to it.
 */
class RingerModeTracker(private val context: Context) {
    private val events = ConcurrentLinkedQueue<RingerState>()
    private val reducer = StateTransitionReducer<RingerState>()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!registered || intent.action != AudioManager.RINGER_MODE_CHANGED_ACTION) return
            val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)
            accept(RingerState.fromAudioMode(mode))
        }
    }

    @Synchronized
    private fun accept(state: RingerState?) {
        reducer.observe(state, System.currentTimeMillis(), OwnSoundChangeGuard.lastMark())?.let(events::add)
    }

    @Synchronized
    fun start() {
        if (registered) return
        // Baseline first: the current mode, or a sticky delivery of it, must never fire.
        reducer.reset()
        reducer.seed(RingerState.fromAudioMode(currentMode()))
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun drainEvents(): List<RingerState> = buildList {
        while (true) add(events.poll() ?: break)
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } finally {
            registered = false
            events.clear()
            reducer.reset()
        }
    }

    private fun currentMode(): Int =
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode ?: -1
}

/**
 * Runtime receiver for Do Not Disturb (interruption filter) changes, with the same lifecycle as
 * [RingerModeTracker]. Reading the filter needs no special permission.
 */
class DndStateTracker(private val context: Context) {
    private val events = ConcurrentLinkedQueue<DndState>()
    private val reducer = StateTransitionReducer<DndState>()
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!registered || intent.action != NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) return
            accept(DndState.fromInterruptionFilter(currentFilter()))
        }
    }

    @Synchronized
    private fun accept(state: DndState?) {
        reducer.observe(state, System.currentTimeMillis(), OwnSoundChangeGuard.lastMark())?.let(events::add)
    }

    @Synchronized
    fun start() {
        if (registered) return
        reducer.reset()
        reducer.seed(DndState.fromInterruptionFilter(currentFilter()))
        val filter = IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun drainEvents(): List<DndState> = buildList {
        while (true) add(events.poll() ?: break)
    }

    @Synchronized
    fun stop() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } finally {
            registered = false
            events.clear()
            reducer.reset()
        }
    }

    private fun currentFilter(): Int =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
}
