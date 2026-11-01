package com.flowpilot.app.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Tracks device shake motion events via accelerometer.
 * Dynamically registers sensor only when active shake rules exist and screen is on.
 */
class DeviceShakeTracker(
    context: Context,
    initialThreshold: Float = ShakeReducer.DEFAULT_THRESHOLD_ACCEL,
    private val debounceMs: Long = ShakeReducer.DEFAULT_DEBOUNCE_MS,
) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    private var threshold: Float = initialThreshold

    private val events = ConcurrentLinkedQueue<ShakeEvent>()

    @Volatile
    private var registered: Boolean = false

    private val stateLock = Any()
    private var state: ShakeState = ShakeState()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!registered || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            val x = event.values.getOrNull(0) ?: return
            val y = event.values.getOrNull(1) ?: return
            val z = event.values.getOrNull(2) ?: return
            val now = System.currentTimeMillis()

            val netAccel = ShakeReducer.calculateNetAcceleration(x, y, z)
            val didShake = synchronized(stateLock) {
                val (nextState, triggered) = ShakeReducer.evaluateNet(
                    state = state,
                    netAccel = netAccel,
                    nowMs = now,
                    threshold = threshold,
                    debounceMs = debounceMs,
                )
                state = nextState
                triggered
            }

            if (didShake) {
                Log.d("DeviceShakeTracker", "Shake detected at $now (%.1f m/s^2)".format(netAccel))
                events.add(ShakeEvent(timestampMs = now, magnitude = netAccel))
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /**
     * Applies the softest force any enabled shake rule needs; resets the detector so an in-flight
     * burst cannot leak across a rule change.
     */
    fun updateThreshold(value: Float) {
        val thresholdValue = ShakeReducer.clampThreshold(value)
        if (thresholdValue == threshold) return
        threshold = thresholdValue
        synchronized(stateLock) { state = ShakeState() }
        Log.i("DeviceShakeTracker", "Shake threshold set to $thresholdValue m/s^2")
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
        if (registered || accelerometer == null || sensorManager == null) return
        registered = sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        Log.i("DeviceShakeTracker", "Registered accelerometer for shake detection")
    }

    fun stop() {
        if (!registered || sensorManager == null) return
        sensorManager.unregisterListener(sensorListener)
        registered = false
        synchronized(stateLock) { state = ShakeState() }
        events.clear()
        Log.i("DeviceShakeTracker", "Unregistered accelerometer for shake detection")
    }

    fun drainEvents(): List<ShakeEvent> {
        if (events.isEmpty()) return emptyList()
        val drained = mutableListOf<ShakeEvent>()
        while (true) {
            val event = events.poll() ?: break
            drained.add(event)
        }
        return drained
    }
}
