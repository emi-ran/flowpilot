package com.flowpilot.app.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

/**
 * Tracks device physical orientation changes (face down on a surface vs face up).
 * Uses Proximity and Accelerometer/Gravity sensors with intelligent lifecycle:
 * dynamically unregisters sensors when no active rules exist or when screen is off
 * (unless a rule explicitly allows screen-off detection).
 */
class DeviceFlipTracker(
    context: Context,
    private val debounceMs: Long = DeviceFlipReducer.DEFAULT_DEBOUNCE_MS,
) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometerSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val events = ConcurrentLinkedQueue<FlipEvent>()

    @Volatile
    private var registered: Boolean = false

    @Volatile
    private var isNear: Boolean = false

    @Volatile
    private var gravityX: Float = 0f

    @Volatile
    private var gravityY: Float = 0f

    @Volatile
    private var gravityZ: Float = 9.8f

    private val stateLock = Any()
    private var state: FlipTrackerState = FlipTrackerState()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!registered) return
            when (event.sensor.type) {
                Sensor.TYPE_PROXIMITY -> {
                    val distance = event.values.getOrNull(0) ?: return
                    val maxRange = proximitySensor?.maximumRange ?: 5.0f
                    // Binary sensors typically return 0 (near) or maxRange (far)
                    // Continuous sensors return cm distance
                    isNear = distance < min(maxRange, 4.0f)
                }
                Sensor.TYPE_GRAVITY,
                Sensor.TYPE_ACCELEROMETER -> {
                    gravityX = event.values.getOrNull(0) ?: 0f
                    gravityY = event.values.getOrNull(1) ?: 0f
                    gravityZ = event.values.getOrNull(2) ?: 9.8f
                }
                else -> return
            }

            evaluateCurrentState()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun evaluateCurrentState() {
        val now = System.currentTimeMillis()
        val raw = DeviceFlipReducer.determineRawOrientation(
            isNear = isNear,
            x = gravityX,
            y = gravityY,
            z = gravityZ,
        )

        val flipEvent: FlipEvent? = synchronized(stateLock) {
            val (nextState, event) = DeviceFlipReducer.reduce(
                state = state,
                rawOrientation = raw,
                timestampMs = now,
                debounceMs = debounceMs,
            )
            state = nextState
            event
        }

        if (flipEvent != null) {
            Log.d(TAG, "Flip event detected: $flipEvent")
            events.add(flipEvent)
        }
    }

    /**
     * Dynamically updates whether sensors should be actively registered based on:
     * - Whether any enabled rules use flip triggers
     * - Whether any rule allows detection with screen off
     * - Live screen state
     */
    fun updateListeningPolicy(
        hasActiveRules: Boolean,
        anyAllowScreenOff: Boolean,
        isScreenOn: Boolean,
    ) {
        val shouldListen = hasActiveRules && (isScreenOn || anyAllowScreenOff)
        if (shouldListen && !registered) {
            registerSensors()
        } else if (!shouldListen && registered) {
            stop()
        }
    }

    fun start() {
        // Tracker started; actual sensor registration happens via updateListeningPolicy
    }

    fun stop() {
        unregisterSensors()
        synchronized(stateLock) {
            state = FlipTrackerState()
        }
        events.clear()
        isNear = false
        gravityX = 0f
        gravityY = 0f
        gravityZ = 9.8f
    }

    private fun registerSensors() {
        if (registered || sensorManager == null) return
        val sm = sensorManager

        var anyRegistered = false
        proximitySensor?.let { prox ->
            val ok = sm.registerListener(sensorListener, prox, SensorManager.SENSOR_DELAY_NORMAL)
            if (ok) anyRegistered = true
        }
        accelerometerSensor?.let { acc ->
            val ok = sm.registerListener(sensorListener, acc, SensorManager.SENSOR_DELAY_NORMAL)
            if (ok) anyRegistered = true
        }

        if (anyRegistered) {
            registered = true
            Log.i(TAG, "DeviceFlipTracker registered sensors (SENSOR_DELAY_NORMAL)")
        }
    }

    private fun unregisterSensors() {
        if (!registered || sensorManager == null) return
        sensorManager.unregisterListener(sensorListener)
        registered = false
        Log.i(TAG, "DeviceFlipTracker unregistered sensors to save battery")
    }

    fun drainEvents(): List<FlipEvent> = buildList {
        while (true) {
            add(events.poll() ?: break)
        }
    }

    companion object {
        private const val TAG = "DeviceFlipTracker"
    }
}
