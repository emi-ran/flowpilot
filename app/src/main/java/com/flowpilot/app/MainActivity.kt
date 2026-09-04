package com.flowpilot.app

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
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
import com.flowpilot.app.ui.FlowPilotRoot
import com.flowpilot.app.ui.theme.FlowPilotTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(requestListener)
        initNfcForegroundDispatch()
        handleNfcIntent(intent)
        setContent {
            val vm: com.flowpilot.app.ui.AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val appLanguage by vm.appLanguage.collectAsState()
            com.flowpilot.app.ui.util.AppLocaleProvider(appLanguage) {
                FlowPilotTheme { FlowPilotRoot(vm) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNfcIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        enableNfcForegroundDispatch()
    }

    override fun onPause() {
        disableNfcForegroundDispatch()
        super.onPause()
    }

    private fun initNfcForegroundDispatch() {
        val manager = getSystemService(android.nfc.NfcManager::class.java)
        nfcAdapter = manager?.defaultAdapter ?: NfcAdapter.getDefaultAdapter(this)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val explicitIntent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            setPackage(packageName)
        }
        pendingIntent = PendingIntent.getActivity(this, 0, explicitIntent, flags)

    }

    private fun enableNfcForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        val pi = pendingIntent ?: return
        if (adapter.isEnabled) {
            try {
                // Null filters/tech lists capture every supported tag while this activity is foreground.
                // Background delivery stays limited to manifest TAG/TECH filters.
                adapter.enableForegroundDispatch(this, pi, null, null)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "NFC foreground dispatch unavailable while activity is not resumed", e)
            }
        }
    }

    private fun disableNfcForegroundDispatch() {
        val adapter = nfcAdapter ?: return
        try {
            adapter.disableForegroundDispatch(this)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "NFC foreground dispatch already disabled", e)
        }
    }

    private fun handleNfcIntent(intent: Intent?) {
        intent ?: return
        val action = intent.action ?: return
        if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            val rawId: ByteArray? = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)?.id
                } else {
                    @Suppress("DEPRECATION")
                    (intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag)?.id
            }

            if (rawId != null && rawId.isNotEmpty()) {
                NfcTagHandoff.emitTagScanned(rawId)
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
    }
}
