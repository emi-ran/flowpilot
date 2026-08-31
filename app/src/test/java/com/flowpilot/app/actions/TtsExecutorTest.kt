package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TtsExecutorTest {

    @Test
    fun execute_returnsFailure_whenCacheFileNameEmpty() {
        val context = RuntimeEnvironment.getApplication()
        val executor = TtsExecutor(context, playFile = { true })
        val result = executor.execute(
            ActionType.SPEAK_TEXT,
            ActionParameters(ttsAudioFileName = "")
        )
        assertThat(result.success).isFalse()
        assertThat(result.message).contains("missing")
    }

    @Test
    fun execute_returnsFailure_whenCacheFileNameUnsafeOrInvalid() {
        val context = RuntimeEnvironment.getApplication()
        val executor = TtsExecutor(context, playFile = { true })
        val resultTraversal = executor.execute(
            ActionType.SPEAK_TEXT,
            ActionParameters(ttsAudioFileName = "../secret.wav")
        )
        assertThat(resultTraversal.success).isFalse()
        assertThat(resultTraversal.message).contains("invalid or unsafe")

        val resultInvalidFormat = executor.execute(
            ActionType.SPEAK_TEXT,
            ActionParameters(ttsAudioFileName = "random_name.wav")
        )
        assertThat(resultInvalidFormat.success).isFalse()
        assertThat(resultInvalidFormat.message).contains("invalid or unsafe")
    }

    @Test
    fun ttsManager_rejectsUnsafeCacheFileNames() {
        val context = RuntimeEnvironment.getApplication()
        val manager = TtsManager(context)

        assertThat(manager.isValidCacheFileName("../../etc/passwd")).isFalse()
        assertThat(manager.isValidCacheFileName("tts_rule1_invalid")).isFalse()
        assertThat(manager.isValidCacheFileName("tts_preview_0123456789abcdef.wav")).isTrue()

        assertThat(manager.getCacheFile("../../etc/passwd")).isNull()
        assertThat(manager.getCacheFile("tts_rule-1_0123456789abcdef.wav")).isNotNull()
    }

    @Test
    fun execute_returnsFailure_whenCachedFileDoesNotExist() {
        val context = RuntimeEnvironment.getApplication()
        val executor = TtsExecutor(context, playFile = { true })
        val result = executor.execute(
            ActionType.SPEAK_TEXT,
            ActionParameters(ttsAudioFileName = "tts_rule-1_0123456789abcdef.wav")
        )
        assertThat(result.success).isFalse()
        assertThat(result.message).contains("missing or empty")
    }

    @Test
    fun execute_playsOfflineCache_whenFileExists() {
        val context = RuntimeEnvironment.getApplication()
        val manager = TtsManager(context)
        val fileName = manager.computeCacheFileName("rule-123", "Hello FlowPilot", "en-us-x-sfg", 1.0f)
        val file = requireNotNull(manager.getCacheFile(fileName))
        file.writeBytes(byteArrayOf(1, 2, 3, 4)) // Non-empty dummy audio file

        var playedFile: File? = null
        val executor = TtsExecutor(
            context,
            ttsManager = manager,
            playFile = { f ->
                playedFile = f
                true
            }
        )

        val result = executor.execute(
            ActionType.SPEAK_TEXT,
            ActionParameters(
                ttsText = "Hello FlowPilot",
                ttsVoiceName = "en-us-x-sfg",
                ttsSpeechRate = 1.0f,
                ttsAudioFileName = fileName
            )
        )

        assertThat(result.success).isTrue()
        assertThat(playedFile).isNotNull()
        assertThat(playedFile?.name).isEqualTo(fileName)

        file.delete()
    }

    @Test
    fun execute_returnsFailure_whenPlayerReportsFailure() {
        val context = RuntimeEnvironment.getApplication()
        val manager = TtsManager(context)
        val fileName = manager.computeCacheFileName("rule-456", "Test", "default", 1.0f)
        val file = requireNotNull(manager.getCacheFile(fileName))
        file.writeBytes(byteArrayOf(1, 2, 3))

        val executor = TtsExecutor(
            context,
            ttsManager = manager,
            playFile = { false }
        )

        val result = executor.execute(
            ActionType.SPEAK_TEXT,
            ActionParameters(ttsAudioFileName = fileName)
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("failed")

        file.delete()
    }

    @Test
    fun stopPreview_isSafe_whenNoActivePlayer() {
        val context = RuntimeEnvironment.getApplication()
        val executor = TtsExecutor(context)
        executor.stopPreview()
    }
}
