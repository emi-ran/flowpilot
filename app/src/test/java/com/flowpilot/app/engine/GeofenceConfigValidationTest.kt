package com.flowpilot.app.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceConfigValidationTest {

    @Test
    fun isValidGeofenceConfig_validInputsReturnTrue() {
        // Typical Istanbul coordinates
        assertTrue(isValidGeofenceConfig(41.0082, 28.9784, 150))
        // Boundary coordinates
        assertTrue(isValidGeofenceConfig(90.0, 180.0, 1000))
        assertTrue(isValidGeofenceConfig(-90.0, -180.0, 50))
        assertTrue(isValidGeofenceConfig(0.0, 28.9784, 100))
        assertTrue(isValidGeofenceConfig(41.0082, 0.0, 250))
    }

    @Test
    fun isValidGeofenceConfig_invalidLatitudeRejected() {
        assertFalse(isValidGeofenceConfig(90.0001, 28.9784, 150))
        assertFalse(isValidGeofenceConfig(-90.0001, 28.9784, 150))
        assertFalse(isValidGeofenceConfig(180.0, 28.9784, 150))
    }

    @Test
    fun isValidGeofenceConfig_invalidLongitudeRejected() {
        assertFalse(isValidGeofenceConfig(41.0082, 180.0001, 150))
        assertFalse(isValidGeofenceConfig(41.0082, -180.0001, 150))
        assertFalse(isValidGeofenceConfig(41.0082, 360.0, 150))
    }

    @Test
    fun isValidGeofenceConfig_nonFiniteValuesRejected() {
        assertFalse(isValidGeofenceConfig(Double.NaN, 28.9784, 150))
        assertFalse(isValidGeofenceConfig(41.0082, Double.NaN, 150))
        assertFalse(isValidGeofenceConfig(Double.POSITIVE_INFINITY, 28.9784, 150))
        assertFalse(isValidGeofenceConfig(41.0082, Double.NEGATIVE_INFINITY, 150))
    }

    @Test
    fun isValidGeofenceConfig_uninitializedZeroCoordinatesRejected() {
        assertFalse(isValidGeofenceConfig(0.0, 0.0, 150))
    }

    @Test
    fun isValidGeofenceConfig_radiusOutOfRangeRejected() {
        assertFalse(isValidGeofenceConfig(41.0082, 28.9784, 49))
        assertFalse(isValidGeofenceConfig(41.0082, 28.9784, 1001))
        assertFalse(isValidGeofenceConfig(41.0082, 28.9784, 0))
        assertFalse(isValidGeofenceConfig(41.0082, 28.9784, -100))
    }
}
