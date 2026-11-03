package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.flowpilot.app.data.backup.ImportStrategy
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationRepositoryTtsTest {

    private lateinit var context: Context
    private lateinit var repository: AutomationRepository

    private val legacyFileName = "tts_legacy_0123456789abcdef.wav"

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        repository = AutomationRepository(context)
    }

    @After
    fun tearDown() = runTest {
        repository.rawDataStore.edit { it.clear() }
        context.filesDir.resolve("tts_cache").deleteRecursively()
    }

    private fun importedRule(
        id: String,
        ttsText: String,
        ttsAudioFileName: String,
    ) = Automation(
        id = id,
        name = "Imported speech",
        triggerEvent = TriggerEvent.NOTIFICATION_RECEIVED,
        actions = listOf(ActionType.SPEAK_TEXT),
        action = ActionType.SPEAK_TEXT,
        ttsText = ttsText,
        ttsSpeechRate = 1.0f,
        ttsAudioFileName = ttsAudioFileName,
        createdAt = 1L,
    )

    @Test
    fun duplicate_dynamicRule_clearsAudioWithoutThrowing() = runTest {
        val source = repository.add(
            name = "Dynamic speech",
            triggerEvent = TriggerEvent.NOTIFICATION_RECEIVED,
            appPackage = "",
            appName = "",
            actions = listOf(ActionType.SPEAK_TEXT),
            ttsText = "\${notification.text|trim}",
            ttsSpeechRate = 1.0f,
            ttsAudioFileName = legacyFileName,
        )

        val clone = repository.duplicate(source.id, "Dynamic copy")

        assertThat(clone).isNotNull()
        assertThat(clone!!.ttsText).isEqualTo("\${notification.text|trim}")
        assertThat(clone.ttsAudioFileName).isEmpty()
    }

    @Test
    fun duplicate_legacyRuleWithMissingAudio_clearsReferenceWithoutThrowing() = runTest {
        val source = repository.add(
            name = "Legacy speech",
            triggerEvent = TriggerEvent.NOTIFICATION_RECEIVED,
            appPackage = "",
            appName = "",
            actions = listOf(ActionType.SPEAK_TEXT),
            ttsText = "",
            ttsSpeechRate = 1.0f,
            ttsAudioFileName = legacyFileName,
        )

        val clone = repository.duplicate(source.id, "Legacy copy")

        assertThat(clone).isNotNull()
        assertThat(clone!!.ttsText).isEmpty()
        assertThat(clone.ttsAudioFileName).isEmpty()
    }

    @Test
    fun import_dynamicRule_doesNotCopyAudioAndLeavesReferenceEmpty() = runTest {
        val sourceAudio = requireNotNull(
            com.flowpilot.app.actions.TtsManager(context).getCacheFile(legacyFileName),
        )
        sourceAudio.writeBytes(byteArrayOf(1, 2, 3))
        try {
            val result = repository.importAutomations(
                listOf(
                    importedRule(
                        id = "import-dynamic",
                        ttsText = "\${notification.text|trim}",
                        ttsAudioFileName = legacyFileName,
                    ),
                ),
                ImportStrategy.MERGE,
            )

            assertThat(result.count).isEqualTo(1)
            assertThat(result.missingTtsCount).isEqualTo(0)
            val stored = repository.automations.first().first { it.name == "Imported speech" }
            assertThat(stored.ttsText).isEqualTo("\${notification.text|trim}")
            assertThat(stored.ttsAudioFileName).isEmpty()
        } finally {
            sourceAudio.delete()
        }
    }

    @Test
    fun import_legacyRuleWithMissingAudio_clearsReferenceAndCountsInformationalMissing() = runTest {
        val result = repository.importAutomations(
            listOf(
                importedRule(
                    id = "import-legacy",
                    ttsText = "",
                    ttsAudioFileName = legacyFileName,
                ),
            ),
            ImportStrategy.MERGE,
        )

        assertThat(result.count).isEqualTo(1)
        assertThat(result.missingTtsCount).isEqualTo(1)
        val stored = repository.automations.first().first { it.name == "Imported speech" }
        assertThat(stored.ttsText).isEmpty()
        assertThat(stored.ttsAudioFileName).isEmpty()
    }
}
