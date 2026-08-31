package com.flowpilot.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.util.concurrent.ConcurrentLinkedQueue

enum class ScreenEvent { ON, OFF }

/** Receives screen on/off transitions only while automation engine runs; no state is replayed at startup. */
class ScreenStateTracker(private val context: Context) {
    private val events = ConcurrentLinkedQueue<ScreenEvent>()
    private var registered = false
    private var lastEvent: ScreenEvent? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = when (intent.action) {
                Intent.ACTION_SCREEN_ON -> ScreenEvent.ON
                Intent.ACTION_SCREEN_OFF -> ScreenEvent.OFF
                else -> return
            }
            if (event != lastEvent) {
                lastEvent = event
                events.add(event)
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun drainEvents(): List<ScreenEvent> = buildList { while (true) add(events.poll() ?: break) }

    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
        events.clear()
    }
}
