package com.flowpilot.app.engine

/** Prevents an NFC intent from being re-delivered through automatic ReaderMode in same activity session. */
class NfcIntentSession {
    private var intentOriginated = false

    fun markIntentOriginated() {
        intentOriginated = true
    }

    val readerModeAllowed: Boolean get() = !intentOriginated

    fun emitReaderModeTag(rawId: ByteArray?, emit: (ByteArray?) -> Boolean): Boolean =
        readerModeAllowed && emit(rawId)
}
