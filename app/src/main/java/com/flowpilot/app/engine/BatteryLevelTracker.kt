package com.flowpilot.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.util.concurrent.ConcurrentLinkedQueue

data class BatteryLevelTransition(val previous: Int, val current: Int)

/** Tracks level changes after seeding current level at engine start. */
class BatteryLevelTracker(private val context: Context) {
    private val transitions = ConcurrentLinkedQueue<BatteryLevelTransition>()
    private var registered = false
    private var currentLevel: Int? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val next = intent.batteryLevel() ?: return
            val previous = currentLevel
            currentLevel = next
            if (previous != null && previous != next) {
                transitions.add(BatteryLevelTransition(previous, next))
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initial = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        currentLevel = initial?.batteryLevel()
        registered = true
    }

    fun drainTransitions(): List<BatteryLevelTransition> = buildList {
        while (true) add(transitions.poll() ?: break)
    }

    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
        transitions.clear()
        currentLevel = null
    }

    private fun Intent.batteryLevel(): Int? {
        val level = getIntExtra("level", -1)
        val scale = getIntExtra("scale", -1)
        return if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else null
    }
}
