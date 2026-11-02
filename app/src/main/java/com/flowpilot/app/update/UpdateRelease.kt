package com.flowpilot.app.update

data class UpdateRelease(
    val tagName: String,
    val version: AppVersion,
    val notes: String,
    val assetSizeBytes: Long,
    val downloadUrl: String?,
    val releaseUrl: String,
) {
    val versionLabel: String
        get() = tagName.removePrefix("v").removePrefix("V").ifBlank { version.toString() }
}
