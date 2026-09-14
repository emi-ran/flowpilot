package com.flowpilot.app.engine

object TriggerTargetMatcher {
    fun wifiTargetMatches(configured: String, observed: String): Boolean =
        configured.isBlank() || configured.trim().equals(observed.trim(), ignoreCase = true)

    fun wifiTargetsOverlap(left: String, right: String): Boolean =
        left.isBlank() || right.isBlank() || wifiTargetMatches(left, right)

    fun bluetoothTargetsMatch(left: String, right: String): Boolean =
        left.trim().equals(right.trim(), ignoreCase = true)

    fun nfcTargetsMatch(left: String, right: String): Boolean =
        NfcTagUtils.normalizeTagId(left) == NfcTagUtils.normalizeTagId(right)

    fun scheduleTargetMatches(
        scheduledMinute: Int,
        scheduledDays: Set<Int>,
        minute: Int,
        day: Int,
    ): Boolean = scheduledMinute == minute && (scheduledDays.isEmpty() || day in scheduledDays)

    fun scheduleTargetsOverlap(
        leftMinute: Int,
        leftDays: Set<Int>,
        rightMinute: Int,
        rightDays: Set<Int>,
    ): Boolean = leftMinute == rightMinute &&
        (leftDays.isEmpty() || rightDays.isEmpty() || leftDays.any(rightDays::contains))

    fun notificationPackageTargetMatches(configured: String, observed: String): Boolean =
        configured.isBlank() || configured == observed

    fun notificationPackageTargetsOverlap(left: String, right: String): Boolean =
        left.isBlank() || right.isBlank() || left == right

    fun notificationKeywordMatches(configured: String, title: String, text: String): Boolean {
        if (configured.isBlank()) return true
        val keyword = configured.trim()
        return title.contains(keyword, ignoreCase = true) || text.contains(keyword, ignoreCase = true)
    }

    // Any two keywords can coexist in one notification title or body.
    @Suppress("UNUSED_PARAMETER")
    fun notificationKeywordsOverlap(left: String, right: String): Boolean = true

    fun notificationKeywordsCertainlyOverlap(left: String, right: String): Boolean =
        left.isBlank() || right.isBlank() || left.trim().equals(right.trim(), ignoreCase = true)
}
