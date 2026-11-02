package com.flowpilot.app.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppVersionTest {

    @Test
    fun parses_optional_v_prefix_and_build_metadata() {
        assertThat(AppVersion.parse("v2.0.0")).isEqualTo(AppVersion(2, 0, 0))
        assertThat(AppVersion.parse("2.0.0+42")).isEqualTo(AppVersion(2, 0, 0))
        assertThat(AppVersion.parse("2.0.0.7")).isEqualTo(AppVersion(2, 0, 0, 7))
    }

    @Test
    fun rejects_malformed_or_too_large_versions() {
        assertThat(AppVersion.parse("")).isNull()
        assertThat(AppVersion.parse("v2")).isEqualTo(AppVersion(2, 0, 0))
        assertThat(AppVersion.parse("2.0.0.0.1")).isNull()
        assertThat(AppVersion.parse("2.0.-1")).isNull()
        assertThat(AppVersion.parse("2.0.1000001")).isNull()
    }

    @Test
    fun stable_release_is_newer_than_prerelease() {
        val stable = AppVersion.parse("2.0.0")!!
        val beta = AppVersion.parse("2.0.0-beta.1")!!

        assertThat(stable > beta).isTrue()
        assertThat(beta < stable).isTrue()
    }

    @Test
    fun build_number_breaks_ties() {
        val build7 = AppVersion.parse("2.0.0.7")!!
        val build8 = AppVersion.parse("2.0.0.8")!!

        assertThat(build8 > build7).isTrue()
    }
}
