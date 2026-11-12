package com.flowpilot.app.actions

import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VariableCatalogTest {
    private val mediaTriggers = listOf(
        TriggerEvent.MEDIA_PLAYING,
        TriggerEvent.MEDIA_PAUSED,
        TriggerEvent.MEDIA_TRACK_CHANGED,
    )

    @Test
    fun mediaVariablesAreAvailableForEachMediaTrigger() {
        mediaTriggers.forEach { trigger ->
            assertThat(VariableCatalog.availableFor(listOf(trigger)).map { it.id }).contains("media")
            assertThat(VariableCatalog.specsFor(listOf(trigger)).map { it.name })
                .containsAtLeast("media.title", "media.artist")
            assertThat(VariableCatalog.isTemplateValid("\${media.title} - \${media.artist}", listOf(trigger)))
                .isTrue()
        }
    }

    @Test
    fun unrelatedTriggersExcludeMediaButMultiTriggerRulesIncludeIt() {
        val unrelated = listOf(TriggerEvent.WIFI_CONNECTED)
        assertThat(VariableCatalog.availableFor(unrelated).map { it.id }).doesNotContain("media")
        assertThat(VariableCatalog.isTemplateValid("\${media.title}", unrelated)).isFalse()

        val mixed = unrelated + TriggerEvent.MEDIA_TRACK_CHANGED
        assertThat(VariableCatalog.specsFor(mixed).map { it.name })
            .containsAtLeast("wifi.ssid", "media.title", "media.artist")
    }

    @Test
    fun mediaSamplesRenderInStrictMode() {
        val result = VariableCatalog.renderSample(
            "\${media.title} - \${media.artist}",
            listOf(TriggerEvent.MEDIA_PLAYING),
        )
        assertThat(result.text).isEqualTo("Example track - Example artist")
        assertThat(result.unknownVariables).isEmpty()
        assertThat(result.parseErrors).isEmpty()
    }

    @Test
    fun transientMediaValuesRenderAndMissingMetadataSupportsDefaults() {
        val context = TemplateContext(
            trigger = TriggerEvent.MEDIA_TRACK_CHANGED.name,
            values = mapOf("media.title" to "Track", "media.artist" to ""),
        )
        val result = TemplateRenderer.renderResult(
            "\${media.title} - \${media.artist|default:\"Unknown artist\"}",
            context,
            RenderMode.STRICT,
        )
        assertThat(result.text).isEqualTo("Track - Unknown artist")
        assertThat(result.unknownVariables).isEmpty()
        assertThat(result.parseErrors).isEmpty()
    }

    @Test
    fun mediaValuesDoNotLeakIntoUnrelatedContexts() {
        val result = TemplateRenderer.renderResult(
            "\${media.title} \${media.artist}",
            TemplateContext(trigger = TriggerEvent.WIFI_CONNECTED.name),
            RenderMode.STRICT,
        )
        assertThat(result.unknownVariables).containsExactly("media.title", "media.artist")
    }
}
