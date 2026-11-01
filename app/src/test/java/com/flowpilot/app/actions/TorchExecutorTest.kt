package com.flowpilot.app.actions

import android.content.Context
import android.hardware.camera2.CameraManager
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

    private class FakeTorchStateStore(initial: Boolean? = null) : TorchStateStore {
        var state: Boolean? = initial
        val writes = mutableListOf<Boolean>()

        override fun read(cameraId: String): Boolean? = state

        override fun write(cameraId: String, enabled: Boolean) {
            state = enabled
            writes.add(enabled)
        }
    }

    private fun cameraManager(): CameraManager =
        RuntimeEnvironment.getApplication()
            .getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private fun executor(
        state: FakeTorchStateStore = FakeTorchStateStore(),
        finder: (CameraManager) -> String? = { "0" },
        setter: (CameraManager, String, Boolean) -> Unit = { _, _, _ -> },
    ) = TorchExecutor(
        context = RuntimeEnvironment.getApplication(),
        cameraManagerProvider = { cameraManager() },
        defaultCameraFinder = finder,
        torchModeSetter = setter,
        torchStateStore = state,
    )

    @Test
    fun on_sets_torch_mode_true_for_found_camera() {
        var toggledCameraId: String? = null
        var toggledState: Boolean? = null
        val store = FakeTorchStateStore()

        val result = executor(store) { _, id, enabled ->
            toggledCameraId = id
            toggledState = enabled
        }.execute(ActionType.TORCH_ON)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Flashlight turned on")
        assertThat(toggledCameraId).isEqualTo("0")
        assertThat(toggledState).isTrue()
        assertThat(store.state).isTrue()
    }

    @Test
    fun off_sets_torch_mode_false_for_found_camera() {
        var toggledCameraId: String? = null
        var toggledState: Boolean? = null
        val store = FakeTorchStateStore(initial = true)

        val result = executor(store) { _, id, enabled ->
            toggledCameraId = id
            toggledState = enabled
        }.execute(ActionType.TORCH_OFF)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Flashlight turned off")
        assertThat(toggledCameraId).isEqualTo("0")
        assertThat(toggledState).isFalse()
        assertThat(store.state).isFalse()
    }

    @Test
    fun toggle_turns_light_on_when_known_state_is_off() {
        var toggledState: Boolean? = null
        val store = FakeTorchStateStore(initial = false)

        val result = executor(store) { _, _, enabled -> toggledState = enabled }
            .execute(ActionType.TORCH_TOGGLE)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Flashlight turned on")
        assertThat(toggledState).isTrue()
        assertThat(store.state).isTrue()
    }

    @Test
    fun toggle_turns_light_off_when_known_state_is_on() {
        var toggledState: Boolean? = null
        val store = FakeTorchStateStore(initial = true)

        val result = executor(store) { _, _, enabled -> toggledState = enabled }
            .execute(ActionType.TORCH_TOGGLE)

        assertThat(result.success).isTrue()
        assertThat(result.message).contains("Flashlight turned off")
        assertThat(toggledState).isFalse()
        assertThat(store.state).isFalse()
    }

    @Test
    fun toggle_treats_unknown_state_as_off() {
        var toggledState: Boolean? = null
        val store = FakeTorchStateStore(initial = null)

        val result = executor(store) { _, _, enabled -> toggledState = enabled }
            .execute(ActionType.TORCH_TOGGLE)

        assertThat(result.success).isTrue()
        assertThat(toggledState).isTrue()
        assertThat(store.state).isTrue()
    }

    @Test
    fun consecutive_toggles_alternate_state() {
        val applied = mutableListOf<Boolean>()
        val store = FakeTorchStateStore(initial = false)
        val torch = executor(store) { _, _, enabled -> applied.add(enabled) }

        torch.execute(ActionType.TORCH_ON)
        torch.execute(ActionType.TORCH_TOGGLE)
        torch.execute(ActionType.TORCH_TOGGLE)

        assertThat(applied).containsExactly(true, false, true).inOrder()
    }

    @Test
    fun failed_toggle_does_not_change_tracked_state() {
        val store = FakeTorchStateStore(initial = true)

        val result = executor(store) { _, _, _ ->
            throw IllegalStateException("camera in use")
        }.execute(ActionType.TORCH_TOGGLE)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("camera in use")
        assertThat(store.state).isTrue()
        assertThat(store.writes).isEmpty()
    }

    @Test
    fun missing_flash_hardware_reports_honest_failure() {
        val result = executor(finder = { null }).execute(ActionType.TORCH_ON)

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("No camera with flash found")
    }
}
