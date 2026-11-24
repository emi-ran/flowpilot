package com.flowpilot.app.diagnostics

import com.flowpilot.app.actions.ActionDiagnosticReason
import com.flowpilot.app.actions.ActionResultCode

/**
 * Stable, machine-readable reason for a failed (or unverified) action. Names are persisted as
 * strings in history, so never rename an entry; add new ones instead.
 */
enum class ReasonCode(val description: String) {
    JSON_PARSE_ERROR("The JSON command text is not valid JSON."),
    JSON_INPUT_TOO_LARGE("The JSON command text exceeds the size limit."),
    JSON_INVALID_SCHEMA("The JSON command does not match the expected structure."),
    JSON_UNSUPPORTED_VERSION("The JSON command version is not supported."),
    JSON_UNSUPPORTED_ACTION("The JSON command names an unknown or disallowed action."),
    JSON_INVALID_PARAMETERS("The JSON command parameters failed validation."),
    JSON_DISPATCH_FAILED("The JSON command was valid but the nested action failed."),
    PERMISSION_MISSING("A required Android permission or special access is missing."),
    SHIZUKU_NOT_RUNNING("Shizuku is not running."),
    SHIZUKU_PERMISSION_MISSING("FlowPilot has not been granted Shizuku permission."),
    EXECUTION_REVOKED("The run was revoked before dispatch (rule changed, disabled or deleted)."),
    CANCELLED("The run was cancelled (engine stopped or rule changed) before this action finished."),
    TARGET_APP_UNAVAILABLE("The target app is not installed or cannot be launched."),
    INVALID_INPUT("An action input was missing or invalid."),
    HANDLER_UNAVAILABLE("No app on the device can handle this request."),
    SYSTEM_COMMAND_FAILED("The system rejected the change or the command failed."),
    STATE_UNAVAILABLE("The resulting system state could not be read back."),
    STATE_MISMATCH("The system state after the change differs from the requested one."),
    VOLUME_BELOW_MINIMUM("The requested volume is below the minimum the device allows."),
    MEDIA_SESSION_UNAVAILABLE("No suitable media session was available."),
    NETWORK_TIMEOUT("The network request timed out."),
    NETWORK_FAILURE("The network request failed."),
    HTTP_ERROR("The server answered with an HTTP error status."),
    INVALID_LINK("The configured link is malformed or unsupported."),
    UNEXPECTED_EXCEPTION("An unexpected exception was thrown while executing the action."),
    NO_EXECUTOR("No executor is registered for this action type."),
    FAILED_BEFORE_DISPATCH("The run failed before any action was dispatched."),
    REQUEST_UNVERIFIED("Android accepted the request but the result cannot be read back."),
    UNKNOWN("The executor reported failure without any diagnostic information."),
    ;

    /** True when the code describes an unverified-but-accepted request rather than a failure. */
    val isInformational: Boolean get() = this == REQUEST_UNVERIFIED

    companion object {
        fun fromName(name: String?): ReasonCode? = name?.let { n -> entries.firstOrNull { it.name == n } }

        fun fromDiagnostic(reason: ActionDiagnosticReason): ReasonCode = when (reason) {
            ActionDiagnosticReason.MALFORMED_LINK,
            ActionDiagnosticReason.INVALID_LINK_COMPONENTS,
            ActionDiagnosticReason.UNSUPPORTED_LINK_HOST,
            ActionDiagnosticReason.INVALID_LINK_PATH,
            ActionDiagnosticReason.MISSING_VIDEO_ID,
            ActionDiagnosticReason.MULTIPLE_VIDEO_IDS,
            ActionDiagnosticReason.INVALID_VIDEO_ID_LENGTH,
            ActionDiagnosticReason.INVALID_VIDEO_ID_CHARACTERS -> INVALID_LINK
            ActionDiagnosticReason.SHIZUKU_NOT_RUNNING -> SHIZUKU_NOT_RUNNING
            ActionDiagnosticReason.SHIZUKU_PERMISSION_REQUIRED -> SHIZUKU_PERMISSION_MISSING
            ActionDiagnosticReason.BLUETOOTH_PERMISSION_REQUIRED,
            ActionDiagnosticReason.SETTINGS_PERMISSION_REQUIRED,
            ActionDiagnosticReason.DND_PERMISSION_REQUIRED,
            ActionDiagnosticReason.VOLUME_PERMISSION_REQUIRED,
            ActionDiagnosticReason.NOTIFICATION_PERMISSION_REQUIRED,
            ActionDiagnosticReason.PHONE_PERMISSION_REQUIRED,
            ActionDiagnosticReason.SMS_PERMISSION_REQUIRED -> PERMISSION_MISSING
            ActionDiagnosticReason.VOLUME_BELOW_MINIMUM -> VOLUME_BELOW_MINIMUM
            ActionDiagnosticReason.SYSTEM_COMMAND_FAILED -> SYSTEM_COMMAND_FAILED
            ActionDiagnosticReason.STATE_UNAVAILABLE -> STATE_UNAVAILABLE
            ActionDiagnosticReason.STATE_MISMATCH -> STATE_MISMATCH
            ActionDiagnosticReason.TARGET_APP_UNAVAILABLE -> TARGET_APP_UNAVAILABLE
            ActionDiagnosticReason.INVALID_INPUT -> INVALID_INPUT
            ActionDiagnosticReason.HANDLER_UNAVAILABLE -> HANDLER_UNAVAILABLE
            ActionDiagnosticReason.MEDIA_SESSION_UNAVAILABLE -> MEDIA_SESSION_UNAVAILABLE
            ActionDiagnosticReason.NETWORK_TIMEOUT -> NETWORK_TIMEOUT
            ActionDiagnosticReason.NETWORK_FAILURE -> NETWORK_FAILURE
        }

        /** Derives a code from legacy-compatible result metadata; null for plain success. */
        fun derive(success: Boolean, resultCode: ActionResultCode?, diagnostic: ActionDiagnosticReason?): ReasonCode? = when {
            success && resultCode == ActionResultCode.REQUEST_UNVERIFIED -> REQUEST_UNVERIFIED
            success -> null
            diagnostic != null -> fromDiagnostic(diagnostic)
            resultCode == ActionResultCode.PERMISSION_REQUIRED -> PERMISSION_MISSING
            resultCode == ActionResultCode.EXECUTION_CANCELLED -> CANCELLED
            else -> UNKNOWN
        }
    }
}
