package com.flowpilot.app.update

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GitHubReleaseClientTest {

    private val current = AppVersion.parse("2.0.0")!!

    private fun releaseJson(
        tag: String,
        assetUrl: String = "https://github.com/emi-ran/flowpilot/releases/download/$tag/FlowPilot-$tag.apk",
        body: String = "Notes",
    ): String = """
        {
          "tag_name": "$tag",
          "body": "$body",
          "html_url": "https://github.com/emi-ran/flowpilot/releases/tag/$tag",
          "assets": [
            {"name": "FlowPilot-$tag.apk", "size": 1234, "browser_download_url": "$assetUrl"}
          ]
        }
    """.trimIndent()

    @Test
    fun newer_release_with_trusted_apk_is_returned() {
        val client = GitHubReleaseClient(current) { releaseJson("v2.1.0") }
        val release = runBlocking { client.latestUpdate() }.getOrThrow()

        assertThat(release).isNotNull()
        assertThat(release!!.version).isEqualTo(AppVersion.parse("2.1.0"))
        assertThat(release.versionLabel).isEqualTo("2.1.0")
        assertThat(release.assetSizeBytes).isEqualTo(1234L)
        assertThat(release.downloadUrl).contains("/releases/download/v2.1.0/")
    }

    @Test
    fun same_or_older_release_is_not_an_update() {
        val same = GitHubReleaseClient(current) { releaseJson("v2.0.0") }
        val older = GitHubReleaseClient(current) { releaseJson("v1.9.0") }

        assertThat(runBlocking { same.latestUpdate() }.getOrThrow()).isNull()
        assertThat(runBlocking { older.latestUpdate() }.getOrThrow()).isNull()
    }

    @Test
    fun untrusted_asset_url_is_not_offered_for_download() {
        val client = GitHubReleaseClient(current) {
            releaseJson("v2.1.0", assetUrl = "https://evil.example/FlowPilot.apk")
        }
        val release = runBlocking { client.latestUpdate() }.getOrThrow()!!

        assertThat(release.downloadUrl).isNull()
        assertThat(release.releaseUrl).contains("github.com/emi-ran/flowpilot/releases/tag/v2.1.0")
    }

    @Test
    fun network_or_parse_failure_is_a_result_failure() {
        val client = GitHubReleaseClient(current) { error("offline") }
        val result = runBlocking { client.latestUpdate() }

        assertThat(result.isFailure).isTrue()
    }
}
