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
}
