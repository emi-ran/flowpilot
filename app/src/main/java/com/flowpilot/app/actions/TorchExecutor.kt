package com.flowpilot.app.actions

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.flowpilot.app.data.model.ActionType

/**
 * Drives the device camera flash (flashlight/torch) via standard Android CameraManager.setTorchMode.
 * Requires no special permissions or Shizuku; safe and standard across all Android 6.0+ devices.
 *
 * Android has no torch-state getter, so [TorchStateStore] tracks the last known state per camera
 * (including changes made from the system flashlight tile) to resolve toggle requests.
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
    private val torchStateStore: TorchStateStore = CameraTorchStateStore(context),
) : ActionExecutor {

    override val supportedTypes =
        setOf(ActionType.TORCH_ON, ActionType.TORCH_OFF, ActionType.TORCH_TOGGLE)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        // null means "flip the current state" (TORCH_TOGGLE).
        val targetState: Boolean? = when (action) {
            ActionType.TORCH_ON -> true
            ActionType.TORCH_OFF -> false
            ActionType.TORCH_TOGGLE -> null
            else -> return ActionResult(false, "Unsupported action for Torch")
        }

        val cm = cameraManagerProvider(context)
            ?: return ActionResult(false, "Camera service unavailable")

        val cameraId = defaultCameraFinder(cm)
            ?: return ActionResult(false, "No camera with flash found on this device")

        // A never-observed state is treated as off so the first toggle always lights the torch.
        val enabled = targetState ?: !(torchStateStore.read(cameraId) ?: false)

        return try {
            torchModeSetter(cm, cameraId, enabled)
            // Optimistic write: system callbacks correct it if the platform rejects the change.
            torchStateStore.write(cameraId, enabled)
            ActionResult(true, "Flashlight turned ${if (enabled) "on" else "off"}")
        } catch (_: Throwable) {
            ActionResult(false, "Failed to change flashlight state")
        }
    }
}
