package com.flowpilot.app.actions

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.flowpilot.app.data.model.ActionType

/**
 * Toggles the device camera flash (flashlight/torch) via standard Android CameraManager.setTorchMode.
 * Requires no special permissions or Shizuku; safe and standard across all Android 6.0+ devices.
 */
class TorchExecutor(
    private val context: Context,
    private val cameraManagerProvider: (Context) -> CameraManager? = { appContext ->
        appContext.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    },
    private val defaultCameraFinder: (CameraManager) -> String? = { cm ->
        try {
            cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Throwable) {
            null
        }
    },
    private val torchModeSetter: (CameraManager, String, Boolean) -> Unit = { cm, id, enabled ->
        cm.setTorchMode(id, enabled)
    },
) : ActionExecutor {

    override val supportedTypes = setOf(ActionType.TORCH_ON, ActionType.TORCH_OFF)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        val (enabled, label) = when (action) {
            ActionType.TORCH_ON -> true to "on"
            ActionType.TORCH_OFF -> false to "off"
            else -> return ActionResult(false, "Unsupported action for Torch")
        }

        val cm = cameraManagerProvider(context)
            ?: return ActionResult(false, "Camera service unavailable")

        val cameraId = defaultCameraFinder(cm)
            ?: return ActionResult(false, "No camera with flash found on this device")

        return try {
            torchModeSetter(cm, cameraId, enabled)
            ActionResult(true, "Flashlight turned $label")
        } catch (t: Throwable) {
            ActionResult(false, "Flashlight toggle error: ${t.message ?: t.javaClass.simpleName}")
        }
    }
}
