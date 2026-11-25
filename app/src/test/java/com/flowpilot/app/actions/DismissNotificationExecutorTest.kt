package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.NotificationDismissScope
import com.flowpilot.app.engine.ActiveNotificationRef
import com.flowpilot.app.engine.selectNotificationKeysToDismiss
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DismissNotificationExecutorTest {
    private val active = listOf(
        ActiveNotificationRef("k1", "pkg.a"),
        ActiveNotificationRef("k2", "pkg.b"),
        ActiveNotificationRef("k3", "pkg.a"),
    )

    private class Fixture(
        var access: Boolean = true,
        var connected: Boolean = true,
        var active: List<ActiveNotificationRef>? = emptyList(),
        var cancelOk: Boolean = true,
    ) {
        var cancelled: List<String>? = null
        fun executor() = DismissNotificationExecutor(
            hasAccess = { access },
            activeNotifications = { active },
            cancel = { cancelled = it; cancelOk },
            listenerConnected = { connected },
        )
    }

    private fun params(
        key: String = "k1",
        pkg: String = "pkg.a",
        scope: NotificationDismissScope = NotificationDismissScope.THIS,
    ) = ActionParameters(dismissScope = scope, triggerNotificationKey = key, triggerNotificationPackage = pkg)

    @Test fun thisScopeSelectsOnlyTriggerKey() {
        assertThat(selectNotificationKeysToDismiss(NotificationDismissScope.THIS, "k1", "pkg.a", active)).containsExactly("k1")
    }

    @Test fun packageScopeSelectsPackageNotificationsAndTriggerKey() {
        val keys = selectNotificationKeysToDismiss(NotificationDismissScope.ALL_FROM_PACKAGE, "k1", "pkg.a", active)
        assertThat(keys).containsExactly("k1", "k3")
    }

    @Test fun packageScopeIncludesTriggerKeyEvenIfNoLongerActive() {
        val keys = selectNotificationKeysToDismiss(NotificationDismissScope.ALL_FROM_PACKAGE, "gone", "pkg.a", active)
        assertThat(keys).contains("gone")
        assertThat(keys).containsAtLeast("k1", "k3")
        assertThat(keys).doesNotContain("k2")
    }

    @Test fun packageScopeWithoutPackageFallsBackToTriggerKey() {
        assertThat(selectNotificationKeysToDismiss(NotificationDismissScope.ALL_FROM_PACKAGE, "k1", "", active)).containsExactly("k1")
    }

    @Test fun blankKeySelectsNothing() {
        assertThat(selectNotificationKeysToDismiss(NotificationDismissScope.THIS, " ", "pkg.a", active)).isEmpty()
        assertThat(selectNotificationKeysToDismiss(NotificationDismissScope.ALL_FROM_PACKAGE, "", "pkg.a", active)).isEmpty()
    }

    @Test fun nonNotificationTriggerReturnsClearDiagnostic() {
        val f = Fixture()
        val result = f.executor().execute(ActionType.DISMISS_NOTIFICATION, params(key = ""))
        assertThat(result.success).isFalse()
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.NOT_NOTIFICATION_TRIGGER)
        assertThat(f.cancelled).isNull()
    }

    @Test fun missingAccessReturnsPermissionRequired() {
        val f = Fixture(access = false)
        val result = f.executor().execute(ActionType.DISMISS_NOTIFICATION, params())
        assertThat(result.success).isFalse()
        assertThat(result.resultCode).isEqualTo(ActionResultCode.PERMISSION_REQUIRED)
        assertThat(f.cancelled).isNull()
    }

    @Test fun disconnectedListenerReturnsDiagnostic() {
        val f = Fixture(connected = false)
        val result = f.executor().execute(ActionType.DISMISS_NOTIFICATION, params())
        assertThat(result.success).isFalse()
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.NOTIFICATION_LISTENER_DISCONNECTED)
        assertThat(f.cancelled).isNull()
    }

    @Test fun cancelFailureBecauseListenerGoneReturnsDiagnostic() {
        val f = Fixture(cancelOk = false)
        val result = f.executor().execute(ActionType.DISMISS_NOTIFICATION, params())
        assertThat(result.success).isFalse()
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.NOTIFICATION_LISTENER_DISCONNECTED)
    }

    @Test fun thisScopeCancelsTriggerKey() {
        val f = Fixture()
        val result = f.executor().execute(ActionType.DISMISS_NOTIFICATION, params())
        assertThat(result.success).isTrue()
        assertThat(f.cancelled).containsExactly("k1")
        assertThat(result.message).doesNotContain("k1")
    }

    @Test fun packageScopeCancelsActivePackageNotifications() {
        val f = Fixture(active = active)
        val result = f.executor().execute(ActionType.DISMISS_NOTIFICATION, params(scope = NotificationDismissScope.ALL_FROM_PACKAGE))
        assertThat(result.success).isTrue()
        assertThat(f.cancelled).containsExactly("k1", "k3")
    }

    @Test fun packageScopeWithUnavailableActiveListFails() {
        val f = Fixture(active = null)
        val result = f.executor().execute(ActionType.DISMISS_NOTIFICATION, params(scope = NotificationDismissScope.ALL_FROM_PACKAGE))
        assertThat(result.success).isFalse()
        assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.NOTIFICATION_LISTENER_DISCONNECTED)
    }

    @Test fun securityExceptionMapsToPermissionRequired() {
        val executor = DismissNotificationExecutor(
            hasAccess = { true },
            activeNotifications = { emptyList() },
            cancel = { throw SecurityException("x") },
            listenerConnected = { true },
        )
        val result = executor.execute(ActionType.DISMISS_NOTIFICATION, params())
        assertThat(result.resultCode).isEqualTo(ActionResultCode.PERMISSION_REQUIRED)
    }
}
