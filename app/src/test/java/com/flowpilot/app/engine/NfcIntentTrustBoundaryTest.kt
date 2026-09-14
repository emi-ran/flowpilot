package com.flowpilot.app.engine

import android.content.Intent
import android.nfc.NfcAdapter
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NfcIntentTrustBoundaryTest {

    @After
    fun clearNfcState() {
        NfcBackgroundConfirmationGate.dismiss()
        NfcTagHandoff.clear()
    }

    @Test
    fun forgedBackgroundDiscoveryIntentCannotEmitBeforeConfirmation() {
        NfcTagHandoff.clear()
        val forgedIntent = Intent(NfcAdapter.ACTION_TAG_DISCOVERED)
            .putExtra(NfcAdapter.EXTRA_ID, configuredId)

        assertThat(NfcBackgroundConfirmationGate.requestConfirmation(forgedIntent)).isTrue()
        assertThat(NfcBackgroundConfirmationGate.hasPendingConfirmation.value).isTrue()
        assertThat(NfcTagHandoff.drainEvents()).isEmpty()

        NfcBackgroundConfirmationGate.dismiss()
        assertThat(NfcBackgroundConfirmationGate.hasPendingConfirmation.value).isFalse()
        assertThat(NfcTagHandoff.drainEvents()).isEmpty()
    }

    @Test
    fun confirmedBackgroundDiscoveryEmitsAndMatchesConfiguredRule() {
        NfcTagHandoff.clear()
        val rule = Automation(
            id = "nfc-rule",
            name = "NFC rule",
            triggerEvent = TriggerEvent.NFC_TAG_SCANNED,
            nfcTagId = "04A1B21F",
            action = ActionType.VIBRATE,
            createdAt = 1L,
        )

        val backgroundDiscovery = Intent(NfcAdapter.ACTION_TECH_DISCOVERED)
            .putExtra(NfcAdapter.EXTRA_ID, configuredId)
        assertThat(NfcBackgroundConfirmationGate.requestConfirmation(backgroundDiscovery)).isTrue()
        assertThat(NfcTagHandoff.drainEvents()).isEmpty()
        assertThat(NfcBackgroundConfirmationGate.confirm()).isTrue()

        assertThat(RuleEvaluator.evaluateNfcTag(listOf(rule), NfcTagHandoff.drainEvents().single()))
            .containsExactly(rule)
    }

    @Test
    fun intentOriginatedSessionKeepsReaderModeDisabledBeforeAndAfterConfirmation() {
        val session = NfcIntentSession()
        val emitted = mutableListOf<ByteArray?>()
        val discoveryIntent = Intent(NfcAdapter.ACTION_TAG_DISCOVERED)
            .putExtra(NfcAdapter.EXTRA_ID, configuredId)

        assertThat(session.readerModeAllowed).isTrue()
        assertThat(NfcBackgroundConfirmationGate.requestConfirmation(discoveryIntent)).isTrue()
        session.markIntentOriginated()
        assertThat(session.readerModeAllowed).isFalse()
        assertThat(session.emitReaderModeTag(configuredId, emitted::add)).isFalse()
        assertThat(NfcTagHandoff.drainEvents()).isEmpty()
        assertThat(emitted).isEmpty()

        assertThat(NfcBackgroundConfirmationGate.confirm()).isTrue()
        assertThat(session.readerModeAllowed).isFalse()
        assertThat(session.emitReaderModeTag(configuredId, emitted::add)).isFalse()

        NfcBackgroundConfirmationGate.dismiss()
        assertThat(session.readerModeAllowed).isFalse()
        assertThat(session.emitReaderModeTag(configuredId, emitted::add)).isFalse()
        assertThat(emitted).isEmpty()
    }

    private companion object {
        val configuredId = byteArrayOf(0x04, 0xA1.toByte(), 0xB2.toByte(), 0x1F)
    }
}
