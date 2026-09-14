package com.flowpilot.app.engine

import android.content.Intent
import android.nfc.NfcAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds an untrusted NFC discovery request until a visible user confirmation authorizes it. */
object NfcBackgroundConfirmationGate {
    private val _hasPendingConfirmation = MutableStateFlow(false)
    val hasPendingConfirmation: StateFlow<Boolean> = _hasPendingConfirmation.asStateFlow()

    private var pendingTagId: ByteArray? = null

    @Synchronized
    fun requestConfirmation(intent: Intent?): Boolean {
        val tagId = intent?.takeIf { it.action in DISCOVERY_ACTIONS }
            ?.getByteArrayExtra(NfcAdapter.EXTRA_ID)
            ?.takeIf { it.isNotEmpty() }
            ?.copyOf()
            ?: return false
        pendingTagId = tagId
        _hasPendingConfirmation.value = true
        return true
    }

    @Synchronized
    fun confirm(emit: (ByteArray?) -> Boolean = NfcTagHandoff::emitTagScanned): Boolean {
        val tagId = pendingTagId ?: return false
        pendingTagId = null
        _hasPendingConfirmation.value = false
        return emit(tagId)
    }

    @Synchronized
    fun dismiss() {
        pendingTagId = null
        _hasPendingConfirmation.value = false
    }

    private val DISCOVERY_ACTIONS = setOf(
        NfcAdapter.ACTION_TECH_DISCOVERED,
        NfcAdapter.ACTION_TAG_DISCOVERED,
    )
}
