package com.flowpilot.app.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdatePolicyTest {

    private fun release(tag: String, version: String = tag): UpdateRelease {
        val parsed = AppVersion.parse(version)!!
        return UpdateRelease(
            tagName = tag,
            version = parsed,
            notes = "",
            assetSizeBytes = 0L,
            downloadUrl = null,
            releaseUrl = "https://github.com/emi-ran/flowpilot/releases/tag/$tag",
        )
    }

    @Test
    fun auto_check_runs_when_never_checked_or_after_interval() {
        assertThat(UpdatePolicy.isAutoCheckDue(0L, 1_000L)).isTrue()
        assertThat(UpdatePolicy.isAutoCheckDue(1_000L, 1_000L + UpdatePolicy.AUTO_CHECK_INTERVAL_MS - 1)).isFalse()
        assertThat(UpdatePolicy.isAutoCheckDue(1_000L, 1_000L + UpdatePolicy.AUTO_CHECK_INTERVAL_MS)).isTrue()
    }

    @Test
    fun skipped_version_is_not_prompted_automatically_but_manual_check_shows_it() {
        val update = release("v2.0.0")
        assertThat(
            UpdatePolicy.shouldPrompt(
                release = update,
                skippedVersion = "v2.0.0",
                remindAfter = 0L,
                now = 10_000L,
                manual = false,
            ),
        ).isFalse()
        assertThat(
            UpdatePolicy.shouldPrompt(
                release = update,
                skippedVersion = "v2.0.0",
                remindAfter = 0L,
                now = 10_000L,
                manual = true,
            ),
        ).isTrue()
    }

    @Test
    fun reminder_delay_suppresses_until_deadline() {
        val update = release("v2.0.0")
        assertThat(
            UpdatePolicy.shouldPrompt(update, null, remindAfter = 5_000L, now = 4_999L, manual = false),
        ).isFalse()
        assertThat(
            UpdatePolicy.shouldPrompt(update, null, remindAfter = 5_000L, now = 5_000L, manual = false),
        ).isTrue()
    }
}
