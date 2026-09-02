package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.content.Intent
import android.net.Uri

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneExecutorTest {

    @Test
    fun openDialer_launchesActionDialIntent() {
        val app = RuntimeEnvironment.getApplication()
        lateinit var launchedIntent: Intent
        val executor = PhoneExecutor(
            context = app,
            resolveActivity = { true },
            startActivity = { launchedIntent = it },
        )

        val result = executor.execute(ActionType.OPEN_DIALER)
        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Dialer opened")

        assertThat(launchedIntent.action).isEqualTo(Intent.ACTION_DIAL)
        assertThat(launchedIntent.data).isNull()
    }

    @Test
    fun openDialer_returnsFailure_whenNoDialerApplicationResolved() {
        val app = RuntimeEnvironment.getApplication()
        var started = false
        val executor = PhoneExecutor(
            context = app,
            resolveActivity = { false },
            startActivity = { started = true },
        )

        val result = executor.execute(ActionType.OPEN_DIALER)

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("No dialer application found")
        assertThat(started).isFalse()
    }

    @Test
    fun dialNumber_returnsFailure_whenNumberIsBlank() {
        val app = RuntimeEnvironment.getApplication()
        val executor = PhoneExecutor(app)

        val result = executor.execute(ActionType.DIAL_NUMBER, ActionParameters(phoneNumber = "   "))
        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Invalid or empty phone number")
    }

    @Test
    fun dialNumber_returnsFailure_whenNoDialerApplicationResolved() {
        val app = RuntimeEnvironment.getApplication()
        var started = false
        val executor = PhoneExecutor(
            context = app,
            resolveActivity = { false },
            startActivity = { started = true },
        )

        val result = executor.execute(ActionType.DIAL_NUMBER, ActionParameters(phoneNumber = "+905551234567"))

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("No dialer application found")
        assertThat(started).isFalse()
    }

    @Test
    fun dialNumber_launchesActionDialWithTelUri_withoutExposingNumberInResult() {
        val app = RuntimeEnvironment.getApplication()
        lateinit var launchedIntent: Intent
        val executor = PhoneExecutor(
            context = app,
            resolveActivity = { true },
            startActivity = { launchedIntent = it },
        )

        val result = executor.execute(ActionType.DIAL_NUMBER, ActionParameters(phoneNumber = "+905551234567"))
        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Phone number prepared in dialer")
        assertThat(result.message).doesNotContain("+905551234567")
        assertThat(result.message).doesNotContain("+905 •••• 567")

        assertThat(launchedIntent.action).isEqualTo(Intent.ACTION_DIAL)
        assertThat(launchedIntent.data).isEqualTo(Uri.parse("tel:+905551234567"))
    }

    @Test
    fun callNumber_failsGracefully_whenCallPhonePermissionNotGranted() {
        val app = RuntimeEnvironment.getApplication()
        val executor = PhoneExecutor(app)

        // Robolectric doesn't have CALL_PHONE granted by default
        val result = executor.execute(ActionType.CALL_NUMBER, ActionParameters(phoneNumber = "+905551234567"))
        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("Phone call permission required")
    }

    @Test
    fun callNumber_returnsFailure_whenNoPhoneCallingApplicationResolved() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(android.Manifest.permission.CALL_PHONE)
        var started = false
        val executor = PhoneExecutor(
            context = app,
            resolveActivity = { false },
            startActivity = { started = true },
        )

        val result = executor.execute(ActionType.CALL_NUMBER, ActionParameters(phoneNumber = "+905551234567"))

        assertThat(result.success).isFalse()
        assertThat(result.message).isEqualTo("No phone calling application found")
        assertThat(started).isFalse()
    }

    @Test
    fun callNumber_launchesActionCallWithTelUri_withoutExposingNumberInResult_whenPermissionGranted() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(android.Manifest.permission.CALL_PHONE)
        lateinit var launchedIntent: Intent
        val executor = PhoneExecutor(
            context = app,
            resolveActivity = { true },
            startActivity = { launchedIntent = it },
        )

        val result = executor.execute(ActionType.CALL_NUMBER, ActionParameters(phoneNumber = "+905551234567"))
        assertThat(result.success).isTrue()
        assertThat(result.message).isEqualTo("Direct phone call initiated")
        assertThat(result.message).doesNotContain("+905551234567")
        assertThat(result.message).doesNotContain("+905 •••• 567")

        assertThat(launchedIntent.action).isEqualTo(Intent.ACTION_CALL)
        assertThat(launchedIntent.data).isEqualTo(Uri.parse("tel:+905551234567"))
    }
}
