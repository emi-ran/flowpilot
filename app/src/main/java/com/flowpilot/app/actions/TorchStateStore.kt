package com.flowpilot.app.actions

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Last-known camera torch state per camera id.
 *
 * Android exposes no getter for torch mode, so state is observed through
 * [CameraManager.registerTorchCallback], which also reports changes made by the system
 * flashlight tile or other apps.
 */
interface TorchStateStore {
    /** @return true/false when the state is known; null when it has never been observed. */
    fun read(cameraId: String): Boolean?

    fun write(cameraId: String, enabled: Boolean)
}

/** In-memory [TorchStateStore] kept in sync with system torch callbacks. */
class CameraTorchStateStore(context: Context) : TorchStateStore {

    private val states = ConcurrentHashMap<String, Boolean>()

    init {
        val cameraManager =
            context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager != null) {
            try {
                cameraManager.registerTorchCallback(
                    object : CameraManager.TorchCallback() {
                        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                            states[cameraId] = enabled
                        }

                        override fun onTorchModeUnavailable(cameraId: String) {
                            states.remove(cameraId)
                        }
                    },
                    Handler(Looper.getMainLooper()),
                )
                Log.i(TAG, "Registered torch state callback")
            } catch (t: Throwable) {
                Log.w(TAG, "Torch state callback unavailable (${t.javaClass.simpleName})")
            }
        }
    }

    override fun read(cameraId: String): Boolean? = states[cameraId]

    override fun write(cameraId: String, enabled: Boolean) {
        states[cameraId] = enabled
    }

    private companion object {
        const val TAG = "CameraTorchStateStore"
    }
}
