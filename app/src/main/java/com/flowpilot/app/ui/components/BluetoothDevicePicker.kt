package com.flowpilot.app.ui.components

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BondedBluetoothDevice(val address: String, val name: String)

/** Picker exposes bonded devices only. It never starts discovery or persists device-list history. */
@Composable
fun BluetoothDevicePickerField(
    address: String,
    name: String,
    onDeviceSelected: (address: String, name: String) -> Unit,
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    val hasPermission = hasBluetoothConnectPermission(context)
    var selectedDeviceStillBonded by remember(address, hasPermission) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(address, hasPermission) {
        selectedDeviceStillBonded = if (address.isBlank() || !hasPermission) null else {
            withContext(Dispatchers.Default) { isStillBonded(context, address) }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) showPicker = true
    }
    val title = name.ifBlank { if (address.isBlank()) "Bluetooth device" else address }
    val subtitle = if (address.isBlank()) "Choose paired device" else address

    SelectionRow(title, subtitle) {
        if (hasPermission) showPicker = true else permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (!hasPermission) {
        Text(
            "Nearby devices permission required to list bonded Bluetooth devices and detect ACL changes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    } else if (selectedDeviceStillBonded == false) {
        Text(
            "Selected device is no longer paired. This rule will not match until you choose a bonded device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (showPicker) {
        BondedBluetoothDeviceDialog(
            selectedAddress = address,
            onSelected = { selected ->
                onDeviceSelected(selected.address, selected.name)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun BondedBluetoothDeviceDialog(
    selectedAddress: String,
    onSelected: (BondedBluetoothDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var devices by remember { mutableStateOf<List<BondedBluetoothDevice>>(emptyList()) }
    var unavailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.Default) { bondedDevices(context) }
        devices = result.first
        unavailable = result.second
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bluetooth_choose_device_title)) },
        text = {
            when {
                loading -> CircularProgressIndicator()
                unavailable -> Text(stringResource(R.string.bluetooth_unavailable))
                devices.isEmpty() -> Text(stringResource(R.string.bluetooth_no_devices))
                else -> LazyColumn {
                    items(devices, key = { it.address }) { device ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onSelected(device) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (device.address.equals(selectedAddress, true)) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(device.name, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(2.dp))
                                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.btn_done)) } },
    )
}

private fun hasBluetoothConnectPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

private fun bondedDevices(context: Context): Pair<List<BondedBluetoothDevice>, Boolean> {
    if (!hasBluetoothConnectPermission(context)) return emptyList<BondedBluetoothDevice>() to false
    return try {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return emptyList<BondedBluetoothDevice>() to true
        adapter.bondedDevices
            .map { device: BluetoothDevice ->
                BondedBluetoothDevice(
                    address = device.address,
                    name = device.name?.takeIf { it.isNotBlank() } ?: device.address,
                )
            }
            .sortedBy { it.name.lowercase() } to false
    } catch (_: SecurityException) {
        emptyList<BondedBluetoothDevice>() to false
    } catch (_: Throwable) {
        emptyList<BondedBluetoothDevice>() to true
    }
}

private fun isStillBonded(context: Context, address: String): Boolean? {
    return try {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        adapter.bondedDevices.any { it.address.equals(address, ignoreCase = true) }
    } catch (_: SecurityException) {
        null
    } catch (_: Throwable) {
        null
    }
}
