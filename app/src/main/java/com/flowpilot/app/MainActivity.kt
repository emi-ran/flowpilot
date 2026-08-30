package com.flowpilot.app

import android.os.Bundle
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.flowpilot.app.actions.ShizukuPermissionBridge
import com.flowpilot.app.actions.ShizukuShell
import com.flowpilot.app.ui.FlowPilotRoot
import com.flowpilot.app.ui.theme.FlowPilotTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(requestListener)
        setContent { FlowPilotTheme { FlowPilotRoot() } }
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
}