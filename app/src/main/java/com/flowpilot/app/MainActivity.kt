package com.flowpilot.app

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.flowpilot.app.actions.ShizukuPermissionBridge
import com.flowpilot.app.actions.ShizukuShell
import com.flowpilot.app.engine.NfcTagHandoff
import com.flowpilot.app.engine.NfcBackgroundConfirmationGate
import com.flowpilot.app.engine.NfcIntentSession
import com.flowpilot.app.ui.FlowPilotRoot
import com.flowpilot.app.ui.theme.FlowPilotTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private val nfcIntentSession = NfcIntentSession()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(requestListener)
        initNfcReaderMode()
        handleNfcDiscoveryIntent(intent)
        setContent {
            val vm: com.flowpilot.app.ui.AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val appLanguage by vm.appLanguage.collectAsState()
            val appTheme by vm.appTheme.collectAsState()
            val hasPendingNfcConfirmation by NfcBackgroundConfirmationGate.hasPendingConfirmation.collectAsState()

            val isDark = when (appTheme.lowercase()) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            com.flowpilot.app.ui.util.AppLocaleProvider(appLanguage) {
                FlowPilotTheme(darkTheme = isDark) {
                    FlowPilotRoot(vm)
                    if (hasPendingNfcConfirmation) {
                        AlertDialog(
                            onDismissRequest = NfcBackgroundConfirmationGate::dismiss,
                            title = { Text(stringResource(R.string.nfc_background_confirm_title)) },
                            text = { Text(stringResource(R.string.nfc_background_confirm_desc)) },
                            confirmButton = {
                                TextButton(onClick = { NfcBackgroundConfirmationGate.confirm() }) {
                                    Text(stringResource(R.string.nfc_background_confirm_action))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = NfcBackgroundConfirmationGate::dismiss) {
                                    Text(stringResource(R.string.btn_cancel))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (nfcIntentSession.readerModeAllowed) enableNfcReaderMode()
    }

    override fun onPause() {
        disableNfcReaderMode()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcDiscoveryIntent(intent)
    }

    private fun handleNfcDiscoveryIntent(intent: Intent?) {
        // Manifest NFC intents are caller-spoofable. They only open this user-confirmed gate.
        if (NfcBackgroundConfirmationGate.requestConfirmation(intent)) {
            nfcIntentSession.markIntentOriginated()
            disableNfcReaderMode()
        }
    }

    private fun initNfcReaderMode() {
        val manager = getSystemService(android.nfc.NfcManager::class.java)
        nfcAdapter = manager?.defaultAdapter ?: NfcAdapter.getDefaultAdapter(this)
    }

    private fun enableNfcReaderMode() {
        val adapter = nfcAdapter ?: return
        if (adapter.isEnabled) {
            try {
                adapter.enableReaderMode(this, nfcReaderCallback, NFC_READER_FLAGS, null)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "NFC reader mode unavailable while activity is not resumed", e)
            }
        }
    }

    private fun disableNfcReaderMode() {
        val adapter = nfcAdapter ?: return
        try {
            adapter.disableReaderMode(this)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "NFC reader mode already disabled", e)
        }
    }

    private val nfcReaderCallback = NfcAdapter.ReaderCallback { tag: Tag ->
        // ReaderMode invokes this only for a tag discovered by Android's NFC stack. Intent extras are forgeable.
        if (nfcIntentSession.emitReaderModeTag(tag.id, NfcTagHandoff::emitTagScanned)) {
            runOnUiThread {
                Toast.makeText(this, "NFC tag scanned", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ShizukuShell.REQUEST_PERMISSION_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (granted) "Shizuku izni verildi" else "Shizuku izni reddedildi",
                Toast.LENGTH_SHORT,
            ).show()
            ShizukuPermissionBridge.onResult?.invoke(granted)
            ShizukuPermissionBridge.onResult = null
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(requestListener)
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FlowPilotMainActivity"
        const val NFC_READER_FLAGS =
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
    }
}
