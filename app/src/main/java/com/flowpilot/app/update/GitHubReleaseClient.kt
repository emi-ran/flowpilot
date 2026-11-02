package com.flowpilot.app.update

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Reads the latest public GitHub release and returns it only when it is newer than the installed app. */
class GitHubReleaseClient(
    private val currentVersion: AppVersion,
    private val fetcher: (URL) -> String = ::fetchReleaseJson,
) {
    suspend fun latestUpdate(): Result<UpdateRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(RELEASES_API)
            parseLatestRelease(fetcher(url), currentVersion)
        }
    }

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/emi-ran/flowpilot/releases/latest"
        private const val REPOSITORY_RELEASES = "https://github.com/emi-ran/flowpilot/releases"
        private const val MAX_NOTES_CHARS = 8_000

        private val json = Json {
            ignoreUnknownKeys = true
        }

        internal fun parseLatestRelease(body: String, currentVersion: AppVersion): UpdateRelease? {
            val dto = json.decodeFromString(ReleaseDto.serializer(), body)
            val tagName = dto.tagName.trim()
            val version = AppVersion.parse(tagName) ?: error("Release tag has no parseable version")
            if (version <= currentVersion) return null

            val asset = dto.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) &&
                    isAllowedDownloadUrl(asset.browserDownloadUrl)
            }
            return UpdateRelease(
                tagName = tagName,
                version = version,
                notes = dto.body.orEmpty().trim().take(MAX_NOTES_CHARS),
                assetSizeBytes = asset?.size ?: 0L,
                downloadUrl = asset?.browserDownloadUrl,
                releaseUrl = dto.htmlUrl.takeIf(::isAllowedReleaseUrl) ?: REPOSITORY_RELEASES,
            )
        }

        internal fun isAllowedDownloadUrl(raw: String): Boolean = runCatching {
            val uri = URI(raw)
            uri.scheme == "https" &&
                uri.host == "github.com" &&
                uri.path.startsWith("/emi-ran/flowpilot/releases/download/") &&
                uri.userInfo == null &&
                uri.port == -1
        }.getOrDefault(false)

        internal fun isAllowedReleaseUrl(raw: String): Boolean = runCatching {
            val uri = URI(raw)
            uri.scheme == "https" &&
                uri.host == "github.com" &&
                uri.path.startsWith("/emi-ran/flowpilot/releases/") &&
                uri.userInfo == null &&
                uri.port == -1
        }.getOrDefault(false)
    }
}

private const val MAX_RESPONSE_BYTES = 512 * 1024

private fun fetchReleaseJson(url: URL): String {
    require(url.protocol == "https") { "Update checks require HTTPS" }
    val connection = url.openConnection() as HttpsURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.instanceFollowRedirects = false
    connection.useCaches = false
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
    connection.setRequestProperty("User-Agent", "FlowPilot-Android")
    return try {
        val status = connection.responseCode
        if (status != HttpsURLConnection.HTTP_OK) {
            error("GitHub release check failed with HTTP $status")
        }
        connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_RESPONSE_BYTES) {
                    error("GitHub release response is too large")
                }
                output.write(buffer, 0, read)
            }
            output.toString(StandardCharsets.UTF_8.name())
        }
    } finally {
        connection.disconnect()
    }
}

@Serializable
private data class ReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("body") val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("assets") val assets: List<ReleaseAssetDto> = emptyList(),
)

@Serializable
private data class ReleaseAssetDto(
    @SerialName("name") val name: String = "",
    @SerialName("size") val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)
