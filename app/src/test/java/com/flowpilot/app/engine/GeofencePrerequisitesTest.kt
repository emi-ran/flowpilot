package com.flowpilot.app.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class GeofencePrerequisitesTest {

    @Test
    fun evaluateGeofencePrerequisites_fineAndBackgroundReady() {
        val result = evaluateGeofencePrerequisites(
            hasFineLocation = true,
            hasCoarseLocation = false,
            hasBackgroundLocation = true,
            isLocationEnabled = true,
        )
        assertTrue(result is GeofencePrerequisitesResult.Ready)
        assertTrue((result as GeofencePrerequisitesResult.Ready).isFine)
    }

    @Test
    fun evaluateGeofencePrerequisites_coarseOnlyIsRejected() {
        val result = evaluateGeofencePrerequisites(
            hasFineLocation = false,
            hasCoarseLocation = true,
            hasBackgroundLocation = true,
            isLocationEnabled = true,
        )
        assertTrue(result is GeofencePrerequisitesResult.MissingLocationPermission)
    }

    @Test
    fun evaluateGeofencePrerequisites_missingLocationPermission() {
        val result = evaluateGeofencePrerequisites(
            hasFineLocation = false,
            hasCoarseLocation = false,
            hasBackgroundLocation = true,
            isLocationEnabled = true,
        )
        assertTrue(result is GeofencePrerequisitesResult.MissingLocationPermission)
    }

    @Test
    fun evaluateGeofencePrerequisites_missingBackgroundLocation() {
        val result = evaluateGeofencePrerequisites(
            hasFineLocation = true,
            hasCoarseLocation = false,
            hasBackgroundLocation = false,
            isLocationEnabled = true,
        )
        assertTrue(result is GeofencePrerequisitesResult.MissingBackgroundLocation)
    }

    @Test
    fun evaluateGeofencePrerequisites_locationServicesDisabled() {
        val result = evaluateGeofencePrerequisites(
            hasFineLocation = true,
            hasCoarseLocation = true,
            hasBackgroundLocation = true,
            isLocationEnabled = false,
        )
        assertTrue(result is GeofencePrerequisitesResult.LocationServicesDisabled)
    }
}
