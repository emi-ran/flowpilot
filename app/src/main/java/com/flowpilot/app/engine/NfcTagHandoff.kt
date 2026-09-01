package com.flowpilot.app.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Transient in-memory handoff for NFC tag discovery events.
 * Scanned tag IDs are normalized hex strings. No tag payloads (NDEF records, raw tech bytes) are retained.
 */
data class NfcTagScannedEvent(
    val tagId: String,
    val timestamp: Long = System.currentTimeMillis(),
)

object NfcTagHandoff {
    private val queue = ConcurrentLinkedQueue<NfcTagScannedEvent>()
    private val _latestScannedTagId = MutableStateFlow<String?>(null)
    val latestScannedTagId: StateFlow<String?> = _latestScannedTagId.asStateFlow()

    fun emitTagScanned(rawId: ByteArray?) {
        val tagId = NfcTagUtils.formatTagId(rawId)
        if (tagId.isNotEmpty()) {
            queue.add(NfcTagScannedEvent(tagId = tagId))
            _latestScannedTagId.value = tagId
        }
    }

    fun emitTagId(tagId: String) {
        val normalized = NfcTagUtils.normalizeTagId(tagId)
        if (normalized.isNotEmpty()) {
            queue.add(NfcTagScannedEvent(tagId = normalized))
            _latestScannedTagId.value = normalized
        }
    }

    fun drainEvents(): List<NfcTagScannedEvent> = buildList {
        while (true) add(queue.poll() ?: break)
    }

    fun clear() {
        queue.clear()
        clearLatestScannedTagId()
    }

    fun clearLatestScannedTagId() {
        _latestScannedTagId.value = null
    }
}
