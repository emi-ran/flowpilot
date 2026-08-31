package com.flowpilot.app.ui.components

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowpilot.app.engine.WifiStateTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reusable Wi-Fi SSID selector with manual input, on-demand scanning with permission handling,
 * deduplication, and stale/throttled result warnings.
 */
@Composable
fun WifiSsidPickerField(
    ssid: String,
    onSsidChange: (String) -> Unit,
    label: String = "Wi-Fi SSID (leave empty for any Wi-Fi)",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showScanDialog by remember { mutableStateOf(false) }

    val hasFineLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasNearbyWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    } else true

    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val isLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        lm?.isLocationEnabled == true
    } else {
        lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    val wifiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        showScanDialog = true
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = ssid,
            onValueChange = onSsidChange,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            shape = RoundedCornerShape(16.dp),
            label = { Text(label) },
            trailingIcon = {
                IconButton(
                    onClick = {
                        val needed = mutableListOf<String>()
                        if (!hasFineLocation) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNearbyWifi) {
                            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        }
                        if (needed.isNotEmpty()) {
                            wifiLauncher.launch(needed.toTypedArray())
                        } else {
                            showScanDialog = true
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiFind,
                        contentDescription = "Scan nearby networks",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Tap icon or button to pick nearby network",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    val needed = mutableListOf<String>()
                    if (!hasFineLocation) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNearbyWifi) {
                        needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                    if (needed.isNotEmpty()) {
                        wifiLauncher.launch(needed.toTypedArray())
                    } else {
                        showScanDialog = true
                    }
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(Icons.Default.WifiFind, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Scan nearby", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (!hasFineLocation || !isLocationEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "Wi-Fi SSID detection requires ACCESS_FINE_LOCATION permission and device Location enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!hasFineLocation) {
                            OutlinedButton(
                                onClick = {
                                    val perms = mutableListOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_WIFI_STATE,
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                                    }
                                    wifiLauncher.launch(perms.toTypedArray())
                                },
                            ) {
                                Text("Grant Permission", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (!isLocationEnabled) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        val fallback = Intent(Settings.ACTION_SETTINGS)
                                        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(fallback)
                                    }
                                },
                            ) {
                                Text("Enable Location", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScanDialog) {
        WifiScanDialog(
            currentSelectedSsid = ssid,
            onSelectSsid = {
                onSsidChange(it)
                showScanDialog = false
            },
            onDismiss = { showScanDialog = false },
        )
    }
}

sealed interface ScanUiState {
    data object Scanning : ScanUiState
    data class Success(val ssids: List<String>) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

@Composable
fun WifiScanDialog(
    currentSelectedSsid: String,
    onSelectSsid: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var uiState by remember { mutableStateOf<ScanUiState>(ScanUiState.Scanning) }

    fun performScan() {
        uiState = ScanUiState.Scanning
        coroutineScope.launch {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm == null) {
                uiState = ScanUiState.Error("Wi-Fi service unavailable on device.")
                return@launch
            }

            // Query existing / cached scan results first
            fun readCached(): List<String> {
                return try {
                    @Suppress("DEPRECATION")
                    wm.scanResults?.mapNotNull { result ->
                        val cleaned = WifiStateTracker.normalizeSsid(result.SSID)
                        if (WifiStateTracker.isValidSsid(cleaned)) cleaned else null
                    }?.distinct()?.sortedBy { it.lowercase() } ?: emptyList()
                } catch (_: Throwable) {
                    emptyList()
                }
            }

            val hasScanSuccess = try {
                @Suppress("DEPRECATION")
                wm.startScan()
            } catch (e: Exception) {
                false
            }

            if (!hasScanSuccess) {
                // If startScan was throttled by Android system (e.g. 4 scans / 2 min), fallback to available results
                val cached = withContext(Dispatchers.IO) { readCached() }
                if (cached.isNotEmpty()) {
                    uiState = ScanUiState.Success(cached)
                } else {
                    uiState = ScanUiState.Error("Wi-Fi scan throttled or location disabled. You can still type the SSID manually.")
                }
                return@launch
            }

            // Register temporary receiver for SCAN_RESULTS_AVAILABLE_ACTION
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Throwable) {}

                    val results = readCached()
                    uiState = if (results.isNotEmpty()) {
                        ScanUiState.Success(results)
                    } else {
                        ScanUiState.Success(emptyList())
                    }
                }
            }

            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }

            // 5 second fallback in case receiver is never invoked due to OEM broadcast drops
            kotlinx.coroutines.delay(5000L)
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {}

            if (uiState is ScanUiState.Scanning) {
                val results = withContext(Dispatchers.IO) { readCached() }
                uiState = ScanUiState.Success(results)
            }
        }
    }

    LaunchedEffect(Unit) {
        performScan()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.75f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Nearby Wi-Fi Networks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }

                Text(
                    "Select an SSID to populate rule. Android limits scan frequency (scan throttling); results may include recent cached networks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )

                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val state = uiState) {
                        is ScanUiState.Scanning -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Scanning nearby networks...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        is ScanUiState.Error -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Text(
                                    state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = { performScan() }) {
                                    Icon(Icons.Default.Refresh, null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Retry")
                                }
                            }
                        }

                        is ScanUiState.Success -> {
                            if (state.ssids.isEmpty()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    Text(
                                        "No Wi-Fi networks found nearby.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(onClick = { performScan() }) {
                                        Icon(Icons.Default.Refresh, null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Scan again")
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    items(state.ssids, key = { it }) { networkSsid ->
                                        val isSelected = networkSsid.equals(currentSelectedSsid, ignoreCase = true)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSelectSsid(networkSsid) },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainer
                                                }
                                            ),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    Icons.Default.Wifi,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    text = networkSsid,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(20.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { performScan() }, enabled = uiState !is ScanUiState.Scanning) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Rescan")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}