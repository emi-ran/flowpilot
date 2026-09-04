package com.flowpilot.app.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object LocationFetcher {
    private const val TAG = "LocationFetcher"

    /**
     * Retrieves the device coordinates (latitude to longitude).
     *
     * 1. Checks if location permissions are held and location service is enabled.
     * 2. Inspects cached locations from all available providers (GPS, Network, Passive).
     *    If a cached location is very recent (< 60s) and accurate (< 50m), returns immediately.
     * 3. Otherwise, requests an active single location fix from GPS/Network with a timeout.
     * 4. If active fix succeeds within timeout, returns it.
     * 5. If active fix times out, falls back to the best cached location (if any).
     */
    suspend fun getCoordinates(context: Context, timeoutMs: Long = 5000L): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                Log.w(TAG, "Location permissions not granted")
                return@withContext null
            }

            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (lm == null) {
                Log.w(TAG, "LocationManager unavailable")
                return@withContext null
            }

            val isEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                lm.isLocationEnabled
            } else {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
            if (!isEnabled) {
                Log.w(TAG, "Location service is disabled in settings")
                return@withContext null
            }

            // Step 1: Check best cached location
            val cachedLocation = getBestCachedLocation(lm)
            val now = System.currentTimeMillis()
            if (cachedLocation != null) {
                val ageMs = now - cachedLocation.time
                // If cached location is less than 60 seconds old and reasonably accurate, use it immediately
                if (ageMs < 60_000L && cachedLocation.hasAccuracy() && cachedLocation.accuracy <= 50f) {
                    Log.d(TAG, "Using fresh cached location (age: ${ageMs}ms, acc: ${cachedLocation.accuracy}m)")
                    return@withContext cachedLocation.latitude to cachedLocation.longitude
                }
            }

            // Step 2: Actively request a fresh location fix with timeout
            val freshLocation = withTimeoutOrNull(timeoutMs) {
                requestFreshLocation(lm)
            }

            if (freshLocation != null) {
                Log.d(TAG, "Obtained fresh location fix: ${freshLocation.latitude}, ${freshLocation.longitude}")
                return@withContext freshLocation.latitude to freshLocation.longitude
            }

            // Step 3: Fall back to best cached location if fresh request timed out
            if (cachedLocation != null) {
                Log.d(TAG, "Fresh fix timed out; falling back to cached location")
                return@withContext cachedLocation.latitude to cachedLocation.longitude
            }

            Log.w(TAG, "Could not obtain any location fix")
            null
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while accessing location: ${se.message}")
            null
        } catch (t: Throwable) {
            Log.e(TAG, "Error fetching location: ${t.message}")
            null
        }
    }

    private fun getBestCachedLocation(lm: LocationManager): Location? {
        val providers = lm.getProviders(true)
        var best: Location? = null
        for (provider in providers) {
            try {
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null) {
                    best = loc
                } else {
                    val isNewer = loc.time > best.time
                    val isMoreAccurate = loc.hasAccuracy() && (!best.hasAccuracy() || loc.accuracy < best.accuracy)
                    if (isNewer || isMoreAccurate) {
                        best = loc
                    }
                }
            } catch (_: SecurityException) {
            }
        }
        return best
    }

    private suspend fun requestFreshLocation(lm: LocationManager): Location? {
        // Prefer GPS provider, fallback to Network provider
        val provider = when {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

        // On API 30+, use getCurrentLocation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return suspendCancellableCoroutine { cont ->
                val cancellationSignal = android.os.CancellationSignal()
                cont.invokeOnCancellation { cancellationSignal.cancel() }
                try {
                    lm.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        { runnable -> runnable.run() },
                    ) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } catch (se: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

        // Pre-API 30 or fallback: single update listener
        return suspendCancellableCoroutine { cont ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    try {
                        lm.removeUpdates(this)
                    } catch (_: Throwable) {}
                    if (cont.isActive) cont.resume(location)
                }

                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    try {
                        lm.removeUpdates(this)
                    } catch (_: Throwable) {}
                    if (cont.isActive) cont.resume(null)
                }
            }

            cont.invokeOnCancellation {
                try {
                    lm.removeUpdates(listener)
                } catch (_: Throwable) {}
            }

            try {
                lm.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (_: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
