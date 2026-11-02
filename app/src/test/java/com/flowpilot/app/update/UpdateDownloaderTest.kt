package com.flowpilot.app.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateDownloaderTest {

    @Test
    fun safe_file_name_keeps_only_portable_characters() {
        assertThat(UpdateDownloader.safeFileName("v2.1.0")).isEqualTo("FlowPilot-v2.1.0.apk")
        assertThat(UpdateDownloader.safeFileName("v2.1.0/beta 1")).isEqualTo("FlowPilot-v2.1.0_beta_1.apk")
        assertThat(UpdateDownloader.safeFileName("../../etc/passwd")).isEqualTo("FlowPilot-_.._etc_passwd.apk")
        assertThat(UpdateDownloader.safeFileName("")).isEqualTo("FlowPilot-update.apk")
    }
}
