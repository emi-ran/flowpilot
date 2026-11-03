package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.TtsVoiceOption
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TtsRuntimeTest {

    @Test
    fun contentCacheFileName_isStableAcrossWhitespaceVariants() {
        val manager = TtsManager(RuntimeEnvironment.getApplication(), FakeTtsEngine("cache.key"))
        val first = manager.contentCacheFileName("  Hello   runtime \n world  ", "voice-a", 1.0f)
        val second = manager.contentCacheFileName("Hello runtime world", "voice-a", 1.0f)

        assertThat(first).isEqualTo(second)
        assertThat(Regex("^tts_[0-9a-f]{32}\\.wav$").matches(first)).isTrue()
        assertThat(manager.isValidCacheFileName(first)).isTrue()
    }

    @Test
    fun contentCacheFileName_changesWithVoiceRateAndEngine() {
        val context = RuntimeEnvironment.getApplication()
        val manager = TtsManager(context, FakeTtsEngine("engine.one"))
        val differentEngine = TtsManager(context, FakeTtsEngine("engine.two"))
        val baseline = manager.contentCacheFileName("Hello", "voice-a", 1.0f)

        assertThat(manager.contentCacheFileName("Hello", "voice-b", 1.0f)).isNotEqualTo(baseline)
        assertThat(manager.contentCacheFileName("Hello", "voice-a", 1.25f)).isNotEqualTo(baseline)
        assertThat(differentEngine.contentCacheFileName("Hello", "voice-a", 1.0f)).isNotEqualTo(baseline)
    }

    @Test
    fun legacyCacheFileName_remainsByteCompatible() {
        val manager = TtsManager(RuntimeEnvironment.getApplication(), FakeTtsEngine("legacy.key"))
        val legacy = manager.computeCacheFileName("rule-123", "Hello FlowPilot", "en-us-x-sfg", 1.0f)

        assertThat(legacy).isEqualTo("tts_rule-123_027d762385ab0ba2.wav")
        assertThat(manager.isValidCacheFileName(legacy)).isTrue()
        assertThat(manager.isValidCacheFileName("tts_preview_0123456789abcdef.wav")).isTrue()
        assertThat(manager.isValidCacheFileName("tts_0123456789abcdef0123456789abcdef.wav")).isTrue()
        assertThat(manager.isValidCacheFileName("tts_rule1_invalid")).isFalse()
    }

    @Test
    fun ensureSpeech_missSynthesizesOnceAndHitReusesFile() = runTest {
        val engine = FakeTtsEngine("hit.miss")
        val manager = TtsManager(RuntimeEnvironment.getApplication(), engine)

        val first = manager.ensureSpeech("Cache me   now", "voice-a", 1.0f)
        assertThat(first).isNotNull()
        assertThat(first!!.isFile).isTrue()
        assertThat(first.length()).isGreaterThan(0L)
        assertThat(engine.synthesized).hasSize(1)

        val second = manager.ensureSpeech(" Cache me now ", "voice-a", 1.0f)
        assertThat(second).isEqualTo(first)
        assertThat(engine.synthesized).hasSize(1)
        assertThat(engine.synthesized.single().text).isEqualTo("Cache me   now")
    }

    @Test
    fun ensureSpeech_rejectsBlankAndOversizedText() = runTest {
        val engine = FakeTtsEngine("reject.text")
        val manager = TtsManager(RuntimeEnvironment.getApplication(), engine)

        assertThat(manager.ensureSpeech("   ", "voice-a", 1.0f)).isNull()
        assertThat(manager.ensureSpeech("a".repeat(2_001), "voice-a", 1.0f)).isNull()
        assertThat(engine.synthesized).isEmpty()

        val boundary = manager.ensureSpeech("a".repeat(2_000), "voice-a", 1.0f)
        assertThat(boundary).isNotNull()
        assertThat(engine.synthesized).hasSize(1)
    }

    @Test
    fun ensureSpeech_returnsNullAndLeavesNoFileWhenSynthesisFails() = runTest {
        val engine = FakeTtsEngine("failed.synthesis", result = false)
        val manager = TtsManager(RuntimeEnvironment.getApplication(), engine)

        assertThat(manager.ensureSpeech("Will fail", "voice-a", 1.0f)).isNull()
        val expected = requireNotNull(manager.getCacheFile(manager.contentCacheFileName("Will fail", "voice-a", 1.0f)))
        assertThat(expected.exists()).isFalse()
        assertThat(File(expected.parentFile, "${expected.name}.tmp").exists()).isFalse()
    }

    @Test
    fun ensureSpeech_returnsNullWhenEngineReportsSuccessButWritesNothing() = runTest {
        val engine = FakeTtsEngine("empty.output", writeBytes = false)
        val manager = TtsManager(RuntimeEnvironment.getApplication(), engine)

        assertThat(manager.ensureSpeech("Nothing written", "voice-a", 1.0f)).isNull()
        val expected = requireNotNull(
            manager.getCacheFile(manager.contentCacheFileName("Nothing written", "voice-a", 1.0f)),
        )
        assertThat(expected.exists()).isFalse()
    }

    @Test
    fun cleanup_deletesExpiredFilesButKeepsActiveRecentAndTemporaryFiles() {
        val now = 1_700_000_000_000L
        val manager = TtsManager(
            RuntimeEnvironment.getApplication(),
            FakeTtsEngine("cleanup.ttl"),
            clock = { now },
        )
        val dir = cacheDirectory(manager)
        clearDirectory(dir)

        val expired = fileWithAge(dir, "tts_expired_0123456789abcdef.wav", now, DAY_MS * 8)
        val fresh = fileWithAge(dir, "tts_fresh_0123456789abcdef.wav", now, DAY_MS)
        val active = fileWithAge(dir, "tts_active_0123456789abcdef.wav", now, DAY_MS * 8)
        val recent = fileWithAge(dir, "tts_recent_0123456789abcdef.wav", now, 60_000L)
        val temporary = fileWithAge(dir, "tts_expired_0123456789abcdef.wav.tmp", now, DAY_MS * 8)

        manager.cleanup(activeFileNames = setOf(active.name))

        assertThat(expired.exists()).isFalse()
        assertThat(fresh.exists()).isTrue()
        assertThat(active.exists()).isTrue()
        assertThat(recent.exists()).isTrue()
        assertThat(temporary.exists()).isTrue()
    }

    @Test
    fun cleanup_evictsOldestFilesUntilCacheFitsSizeBudget() {
        val now = 1_700_000_000_000L
        val manager = TtsManager(
            RuntimeEnvironment.getApplication(),
            FakeTtsEngine("cleanup.lru"),
            clock = { now },
        )
        val dir = cacheDirectory(manager)
        clearDirectory(dir)

        val megabyte = 1024L * 1024L
        repeat(52) { index ->
            val file = File(dir, "lru_$index.wav")
            RandomAccessFile(file, "rw").use { it.setLength(megabyte) }
            file.setLastModified(now - 60 * 60 * 1_000L - index * 1_000L)
        }

        manager.cleanup()

        assertThat(dir.listFiles()!!.sumOf { it.length() }).isAtMost(50L * megabyte)
        assertThat(File(dir, "lru_51.wav").exists()).isFalse()
        assertThat(File(dir, "lru_50.wav").exists()).isFalse()
        assertThat(File(dir, "lru_0.wav").exists()).isTrue()
    }

    @Test
    fun clearCache_deletesEveryCacheFile() {
        val manager = TtsManager(RuntimeEnvironment.getApplication(), FakeTtsEngine("clear.cache"))
        val dir = cacheDirectory(manager)
        clearDirectory(dir)
        File(dir, "tts_one.wav").writeBytes(byteArrayOf(1))
        File(dir, "tts_two.wav").writeBytes(byteArrayOf(2))

        assertThat(manager.clearCache()).isEqualTo(2)
        assertThat(dir.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun execute_playsLegacyRuleOwnedFile() {
        val context = RuntimeEnvironment.getApplication()
        val manager = TtsManager(context, FakeTtsEngine("legacy.play"))
        val fileName = manager.computeCacheFileName("rule-1", "Legacy speech", "voice-a", 1.0f)
        requireNotNull(manager.getCacheFile(fileName)).writeBytes(byteArrayOf(1, 2, 3))

        var played: File? = null
        val executor = TtsExecutor(context, manager, playFile = { file ->
            played = file
            true
        })

        val result = executor.execute(
            ActionType.SPEAK_TEXT,
            ActionParameters(ttsAudioFileName = fileName),
        )
        assertThat(result.success).isTrue()
        assertThat(played?.name).isEqualTo(fileName)

        played = null
        var asyncResult: ActionResult? = null
        runTest {
            asyncResult = executor.executeAsync(ActionType.SPEAK_TEXT, ActionParameters(ttsAudioFileName = fileName))
        }
        assertThat(asyncResult?.success).isEqualTo(true)
        assertThat(played?.name).isEqualTo(fileName)
    }

    @Test
    fun executeAsync_synthesizesWhenOnlyTtsTextIsSetAndReusesCache() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val engine = FakeTtsEngine("execute.async")
        val manager = TtsManager(context, engine)
        var played: File? = null
        val executor = TtsExecutor(context, manager, playFile = { file ->
            played = file
            true
        })
        val parameters = ActionParameters(
            ttsText = "Runtime speech",
            ttsVoiceName = "voice-a",
            ttsSpeechRate = 1.0f,
        )

        val first = executor.executeAsync(ActionType.SPEAK_TEXT, parameters)
        assertThat(first.success).isTrue()
        assertThat(engine.synthesized).hasSize(1)
        assertThat(played?.name).isEqualTo(manager.contentCacheFileName("Runtime speech", "voice-a", 1.0f))

        val second = executor.executeAsync(ActionType.SPEAK_TEXT, parameters)
        assertThat(second.success).isTrue()
        assertThat(engine.synthesized).hasSize(1)
    }

    @Test
    fun executeAsync_rejectsOversizedTextWithoutSynthesizing() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val engine = FakeTtsEngine("execute.oversize")
        val executor = TtsExecutor(context, TtsManager(context, engine), playFile = { true })

        val result = executor.executeAsync(
            ActionType.SPEAK_TEXT,
            ActionParameters(ttsText = "a".repeat(2_001)),
        )

        assertThat(result.success).isFalse()
        assertThat(result.message).contains("2000")
        assertThat(engine.synthesized).isEmpty()
    }

    private fun cacheDirectory(manager: TtsManager): File = requireNotNull(
        manager.getCacheFile("tts_preview_0123456789abcdef.wav"),
    ).parentFile!!

    private fun clearDirectory(dir: File) {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileWithAge(dir: File, name: String, now: Long, ageMs: Long): File {
        val file = File(dir, name)
        file.writeBytes(byteArrayOf(1, 2, 3))
        check(file.setLastModified(now - ageMs)) { "Could not set timestamp on ${file.name}" }
        return file
    }

    private class FakeTtsEngine(
        override val enginePackageName: String = "test.engine",
        private val result: Boolean = true,
        private val writeBytes: Boolean = true,
    ) : TtsEngine {
        val synthesized = mutableListOf<SynthesisCall>()

        override suspend fun availableVoices(): List<TtsVoiceOption> = emptyList()

        override suspend fun synthesize(
            text: String,
            voiceName: String,
            rate: Float,
            target: File,
        ): Boolean {
            synthesized += SynthesisCall(text, voiceName, rate)
            if (result && writeBytes) target.writeBytes(byteArrayOf(1, 2, 3, 4))
            return result
        }
    }

    private data class SynthesisCall(val text: String, val voiceName: String, val rate: Float)

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1_000L
    }
}
