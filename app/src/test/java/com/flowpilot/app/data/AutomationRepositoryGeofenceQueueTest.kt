package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flowpilot.app.engine.GeofenceDiagnostic
import com.flowpilot.app.engine.GeofenceDiagnosticErrorCode
import com.flowpilot.app.engine.GeofenceDiagnosticStatus
import com.flowpilot.app.engine.GeofenceEvent
import com.flowpilot.app.engine.GeofenceTransition
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationRepositoryGeofenceQueueTest {

    private lateinit var context: Context
    private lateinit var repository: AutomationRepository

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        repository = AutomationRepository(context)
    }

    @After
    fun tearDown() = runTest {
        repository.rawDataStore.edit { it.clear() }
    }

    @Test
    fun enqueueAndDrain_returnsEventsInOrderAndClearsQueue() = runTest {
        val event1 = GeofenceTransition(
            automationId = "rule-1",
            event = GeofenceEvent.ENTER,
            currentLat = 41.0082,
            currentLng = 28.9784,
            timestamp = 1000L,
            coordinatesTimestamp = 900L,
        )
        val event2 = GeofenceTransition(
            automationId = "rule-2",
            event = GeofenceEvent.EXIT,
            currentLat = 41.0100,
            currentLng = 28.9800,
            timestamp = 2000L,
        )

        repository.enqueueGeofenceEvents(listOf(event1, event2))

        val drained = repository.drainGeofenceEvents()
        assertThat(drained).hasSize(2)
        assertThat(drained[0].automationId).isEqualTo("rule-1")
        assertThat(drained[0].event).isEqualTo(GeofenceEvent.ENTER)
        assertThat(drained[0].coordinatesTimestamp).isEqualTo(900L)
        assertThat(drained[1].automationId).isEqualTo("rule-2")
        assertThat(drained[1].event).isEqualTo(GeofenceEvent.EXIT)

        // Queue must be empty on subsequent drain
        val secondDrain = repository.drainGeofenceEvents()
        assertThat(secondDrain).isEmpty()
    }

    @Test
    fun enqueue_boundsQueueCapacityTo50Events() = runTest {
        val bulkEvents = (1..60).map { i ->
            GeofenceTransition(
                automationId = "rule-$i",
                event = if (i % 2 == 0) GeofenceEvent.ENTER else GeofenceEvent.EXIT,
                currentLat = 41.0 + (i * 0.001),
                currentLng = 28.0 + (i * 0.001),
                timestamp = i.toLong(),
            )
        }

        repository.enqueueGeofenceEvents(bulkEvents)

        val drained = repository.drainGeofenceEvents()
        assertThat(drained).hasSize(50)
        // Should retain the most recent 50 (from rule-11 to rule-60)
        assertThat(drained.first().automationId).isEqualTo("rule-11")
        assertThat(drained.last().automationId).isEqualTo("rule-60")
    }

    @Test
    fun enqueue_emptyListDoesNotModifyQueue() = runTest {
        repository.enqueueGeofenceEvents(emptyList())
        val drained = repository.drainGeofenceEvents()
        assertThat(drained).isEmpty()
    }

    @Test
    fun geofenceDiagnostics_persistRegistrationTransitionAndFailure() = runTest {
        repository.recordGeofenceRegistration(listOf("rule-1"), at = 100L)
        repository.recordGeofenceTransitions(
            listOf(GeofenceTransition("rule-1", GeofenceEvent.EXIT, timestamp = 200L))
        )
        repository.recordGeofenceRegistrationFailure(listOf("rule-1"), "Location disabled", at = 300L)

        val diagnostic = repository.geofenceDiagnostics.first()["rule-1"]
        assertThat(diagnostic?.status).isEqualTo(GeofenceDiagnosticStatus.REGISTRATION_FAILED)
        assertThat(diagnostic?.lastRegistrationAt).isEqualTo(100L)
        assertThat(diagnostic?.lastTransition).isEqualTo(GeofenceEvent.EXIT)
        assertThat(diagnostic?.lastTransitionAt).isEqualTo(200L)
        assertThat(diagnostic?.error).isEqualTo("System location services are turned off.")
    }

    @Test
    fun geofenceReceiverError_clearedOnRegistrationOrTransition() = runTest {
        repository.recordGeofenceReceiverError("Geofencing error (1000): GEOFENCE_NOT_AVAILABLE")
        var diagnostics = repository.geofenceDiagnostics.first()
        assertThat(diagnostics[AutomationRepository.GEOFENCE_RECEIVER_DIAGNOSTIC_ID]?.status)
            .isEqualTo(GeofenceDiagnosticStatus.RECEIVER_ERROR)

        // Successful registration clears receiver error
        repository.recordGeofenceRegistration(listOf("rule-1"))
        diagnostics = repository.geofenceDiagnostics.first()
        assertThat(diagnostics[AutomationRepository.GEOFENCE_RECEIVER_DIAGNOSTIC_ID]).isNull()

        // Re-introduce receiver error
        repository.recordGeofenceReceiverError("Geofencing error (1000): GEOFENCE_NOT_AVAILABLE")
        diagnostics = repository.geofenceDiagnostics.first()
        assertThat(diagnostics[AutomationRepository.GEOFENCE_RECEIVER_DIAGNOSTIC_ID]?.status)
            .isEqualTo(GeofenceDiagnosticStatus.RECEIVER_ERROR)

        // Successful transition clears receiver error
        repository.recordGeofenceTransitions(listOf(GeofenceTransition("rule-1", GeofenceEvent.ENTER)))
        diagnostics = repository.geofenceDiagnostics.first()
        assertThat(diagnostics[AutomationRepository.GEOFENCE_RECEIVER_DIAGNOSTIC_ID]).isNull()
    }

    @Test
    fun geofenceDiagnostics_rawExceptionMessageIsSanitizedToAllowlistAndNeverPersisted() = runTest {
        val rawSensitiveFailure = "java.lang.SecurityException: uid 10234 lacks permission for (41.0082, 28.9784) token=secret-xyz"
        repository.recordGeofenceRegistrationFailure(listOf("rule-1"), rawSensitiveFailure, at = 400L)

        val diagnostic = repository.geofenceDiagnostics.first()["rule-1"]
        assertThat(diagnostic?.status).isEqualTo(GeofenceDiagnosticStatus.REGISTRATION_FAILED)
        assertThat(diagnostic?.errorCode).isEqualTo(GeofenceDiagnosticErrorCode.REGISTRATION_FAILED)
        assertThat(diagnostic?.error).isEqualTo(GeofenceDiagnosticErrorCode.REGISTRATION_FAILED.message)
        assertThat(diagnostic?.error).doesNotContain("41.0082")
        assertThat(diagnostic?.error).doesNotContain("secret-xyz")

        val rawPersisted = repository.rawDataStore.data.first()[stringPreferencesKey("geofence_diagnostics")].orEmpty()
        assertThat(rawPersisted).doesNotContain("41.0082")
        assertThat(rawPersisted).doesNotContain("secret-xyz")
    }

    @Test
    fun legacyUnsanitizedGeofenceDiagnostics_migratedOnReadAndWrite() = runTest {
        val geofenceDiagnosticsKey = stringPreferencesKey("geofence_diagnostics")
        val legacyRawJson = """
            [
              {
                "automationId": "rule-legacy",
                "status": "REGISTRATION_FAILED",
                "error": "Security error: Client lacks permission at lat=41.0082, lng=28.9784",
                "updatedAt": 500
              },
              {
                "automationId": "__geofence_receiver__",
                "status": "RECEIVER_ERROR",
                "error": "Failed to handle geofence transitions: SQLiteException near Home 41.0082",
                "updatedAt": 600
              }
            ]
        """.trimIndent()

        repository.rawDataStore.edit { prefs ->
            prefs[geofenceDiagnosticsKey] = legacyRawJson
        }

        val diagnostics = repository.geofenceDiagnostics.first()
        val ruleDiag = diagnostics["rule-legacy"]
        val receiverDiag = diagnostics[AutomationRepository.GEOFENCE_RECEIVER_DIAGNOSTIC_ID]

        assertThat(ruleDiag?.errorCode).isEqualTo(GeofenceDiagnosticErrorCode.REGISTRATION_SECURITY_ERROR)
        assertThat(ruleDiag?.error).isEqualTo(GeofenceDiagnosticErrorCode.REGISTRATION_SECURITY_ERROR.message)
        assertThat(ruleDiag?.error).doesNotContain("41.0082")

        assertThat(receiverDiag?.errorCode).isEqualTo(GeofenceDiagnosticErrorCode.TRANSITION_HANDLING_FAILED)
        assertThat(receiverDiag?.error).isEqualTo(GeofenceDiagnosticErrorCode.TRANSITION_HANDLING_FAILED.message)
        assertThat(receiverDiag?.error).doesNotContain("41.0082")
        assertThat(receiverDiag?.error).doesNotContain("SQLiteException")

        val persistedAfterRead = repository.rawDataStore.data.first()[geofenceDiagnosticsKey].orEmpty()
        assertThat(persistedAfterRead).doesNotContain("41.0082")
        assertThat(persistedAfterRead).doesNotContain("SQLiteException")
    }

    @Test
    fun throwableMapping_extractsKnownPlayServicesStatusCodesAndBlocksRawMessage() {
        class FakeApiException(val code: Int, message: String) : Exception(message) {
            @Suppress("unused")
            fun getStatusCode(): Int = code
        }

        val notAvailable = FakeApiException(1000, "1000: GEOFENCE_NOT_AVAILABLE at (41.0082, 28.9784)")
        val mapped1000 = GeofenceDiagnosticErrorCode.fromThrowable(notAvailable)
        assertThat(mapped1000).isEqualTo(GeofenceDiagnosticErrorCode.GEOFENCE_NOT_AVAILABLE)
        assertThat(mapped1000.message).doesNotContain("41.0082")

        val unknownApi = FakeApiException(9999, "Internal vendor location provider details: secret")
        val mappedUnknown = GeofenceDiagnosticErrorCode.fromThrowable(unknownApi)
        assertThat(mappedUnknown).isEqualTo(GeofenceDiagnosticErrorCode.REGISTRATION_FAILED)
        assertThat(mappedUnknown.message).doesNotContain("secret")

        val secEx = SecurityException("Raw OS security message with user serial 999")
        val mappedSec = GeofenceDiagnosticErrorCode.fromThrowable(secEx, unregisterOperation = true)
        assertThat(mappedSec).isEqualTo(GeofenceDiagnosticErrorCode.UNREGISTER_SECURITY_ERROR)
        assertThat(mappedSec.message).doesNotContain("999")
    }
}
