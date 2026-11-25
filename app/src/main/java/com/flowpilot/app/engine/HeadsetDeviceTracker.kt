package com.flowpilot.app.engine

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Reports headset connect/disconnect transitions (wired, USB, Bluetooth audio outputs) through
 * [AudioManager.registerAudioDeviceCallback] while enabled rules need it. Device names and
 * addresses are never read or logged.
 */
class HeadsetDeviceTracker(private val context: Context) {
    private val events = ConcurrentLinkedQueue<HeadsetEvent>()
    private val reducer = HeadsetStateReducer(Build.VERSION.SDK_INT)
    private var audioManager: AudioManager? = null
    private var registered = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (!registered) return
            reducer.onDevicesAdded(addedDevices.map(::snapshot))?.let(events::add)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (!registered) return
            reducer.onDevicesRemoved(removedDevices.map(::snapshot))?.let(events::add)
        }
    }

    fun start() {
        if (registered) return
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        // Seed first: the initial callback replays existing devices, which must not fire a trigger.
        reducer.seed(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map(::snapshot))
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        audioManager = manager
        registered = true
    }

    fun drainEvents(): List<HeadsetEvent> = buildList {
        while (true) add(events.poll() ?: break)
    }

    fun stop() {
        if (!registered) return
        audioManager?.unregisterAudioDeviceCallback(callback)
        audioManager = null
        registered = false
        events.clear()
        reducer.reset()
    }

    private fun snapshot(device: AudioDeviceInfo) = HeadsetDevice(device.id, device.type, device.isSink)
}
