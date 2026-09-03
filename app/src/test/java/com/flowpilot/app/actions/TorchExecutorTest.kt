package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class TorchExecutorTest {

    @Test
    fun on_sets_torch_mode_true_for_found_camera() {
        var toggledCameraId: String? = null
        var toggledState: Boolean? = null

        val executor = TorchExecutor(
            context = RuntimeEnvironment.getApplication(),
            cameraManagerProvider = {
                it.getSystemService(android.content.Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            },
            defaultCameraFinder = { "0" },
            torchModeSetter = { _, id, enabled ->
                toggledCameraId = id
                toggledState = enabled
            },
        )

        val result = executor.execute(ActionType.TORCH_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Flashlight turned on")
        assertThat(toggledCameraId).isEqualTo("0")
        assertThat(toggledState).isTrue()
    }

    @Test
    fun off_sets_torch_mode_false_for_found_camera() {
        var toggledCameraId: String? = null
        var toggledState: Boolean? = null

        val executor = TorchExecutor(
            context = RuntimeEnvironment.getApplication(),
            cameraManagerProvider = {
                it.getSystemService(android.content.Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            },
            defaultCameraFinder = { "0" },
            torchModeSetter = { _, id, enabled ->
                toggledCameraId = id
                toggledState = enabled
            },
        )

        val result = executor.execute(ActionType.TORCH_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Flashlight turned off")
        assertThat(toggledCameraId).isEqualTo("0")
        assertThat(toggledState).isFalse()
    }

    @Test
    fun missing_flash_hardware_reports_honest_failure() {
        val executor = TorchExecutor(
            context = RuntimeEnvironment.getApplication(),
            cameraManagerProvider = {
                it.getSystemService(android.content.Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            },
            defaultCameraFinder = { null }, // No flash camera found
            torchModeSetter = { _, _, _ -> },
        )

        val result = executor.execute(ActionType.TORCH_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("No camera with flash found")
    }
}
