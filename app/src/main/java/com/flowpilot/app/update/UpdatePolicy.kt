package com.flowpilot.app.update

/** Pure update prompt rules so skip and reminder behavior can be tested without Android. */
internal object UpdatePolicy {
    const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    const val REMIND_INTERVAL_MS = 24L * 60 * 60 * 1000

    fun isAutoCheckDue(lastCheckAt: Long, now: Long): Boolean =
        lastCheckAt <= 0L || now - lastCheckAt >= AUTO_CHECK_INTERVAL_MS

    fun shouldPrompt(
        release: UpdateRelease,
        skippedVersion: String?,
        remindAfter: Long,
        now: Long,
        manual: Boolean,
    ): Boolean {
        if (manual) return true
        if (release.tagName == skippedVersion) return false
        return now >= remindAfter
    }
}
