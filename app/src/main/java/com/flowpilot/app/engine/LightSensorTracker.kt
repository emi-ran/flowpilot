package com.flowpilot.app.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

data class LightTransition(
    val previousLux: Float,
    val currentLux: Float,
)

/**
 * Tracks ambient light (lux) changes via Sensor.TYPE_LIGHT.
 * Edge-triggered: seeds current reading at start and emits transitions only when lux changes.
 */
class LightSensorTracker(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val transitions = ConcurrentLinkedQueue<LightTransition>()

    @Volatile
    private var registered: Boolean = false

    private val stateLock = Any()
    private var lastLux: Float? = null

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_LIGHT) return
            val lux = event.values.getOrNull(0) ?: return

            synchronized(stateLock) {
                val prev = lastLux
                if (prev == null) {
                    // Seed initial state without generating a transition event
                    lastLux = lux
                } else if (prev != lux) {
                    lastLux = lux
                    transitions.add(LightTransition(previousLux = prev, currentLux = lux))
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun updateListeningPolicy(hasActiveRules: Boolean, isScreenOn: Boolean) {
        val shouldListen = hasActiveRules && isScreenOn
        if (shouldListen && !registered) {
            start()
        } else if (!shouldListen && registered) {
            stop()
        }
    }

    fun start() {
        if (registered || lightSensor == null || sensorManager == null) return
        sensorManager.registerListener(sensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
        registered = true
        Log.i("LightSensorTracker", "Registered ambient light sensor")
    }

    fun stop() {
        if (!registered || sensorManager == null) return
        sensorManager.unregisterListener(sensorListener)
        registered = false
        synchronized(stateLock) {
            lastLux = null
        }
        transitions.clear()
        Log.i("LightSensorTracker", "Unregistered ambient light sensor")
    }

    fun drainTransitions(): List<LightTransition> {
        if (transitions.isEmpty()) return emptyList()
        val drained = mutableListOf<LightTransition>()
        while (true) {
            val item = transitions.poll() ?: break
            drained.add(item)
        }
        return drained
    }
}
