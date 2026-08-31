package com.flowpilot.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.util.concurrent.ConcurrentLinkedQueue

enum class ChargerEvent {
    CONNECTED,
    DISCONNECTED,
}

/** Receives charger transitions while the automation service is running. */
class ChargerStateTracker(private val context: Context) {
    private val events = ConcurrentLinkedQueue<ChargerEvent>()
    private var registered = false
    private var lastEvent: ChargerEvent? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> ChargerEvent.CONNECTED
                Intent.ACTION_POWER_DISCONNECTED -> ChargerEvent.DISCONNECTED
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
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun drainEvents(): List<ChargerEvent> = buildList {
        while (true) add(events.poll() ?: break)
    }

    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
        events.clear()
    }
}
