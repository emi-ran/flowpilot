package com.flowpilot.app.ui.util

import com.flowpilot.app.R
import com.flowpilot.app.diagnostics.ReasonCode

val ReasonCode.explanationRes: Int
    get() = when (this) {
        ReasonCode.JSON_PARSE_ERROR -> R.string.history_reason_json_parse_error
        ReasonCode.JSON_INPUT_TOO_LARGE -> R.string.history_reason_json_input_too_large
        ReasonCode.JSON_INVALID_SCHEMA -> R.string.history_reason_json_invalid_schema
        ReasonCode.JSON_UNSUPPORTED_VERSION -> R.string.history_reason_json_unsupported_version
        ReasonCode.JSON_UNSUPPORTED_ACTION -> R.string.history_reason_json_unsupported_action
        ReasonCode.JSON_INVALID_PARAMETERS -> R.string.history_reason_json_invalid_parameters
        ReasonCode.JSON_DISPATCH_FAILED -> R.string.history_reason_json_dispatch_failed
        ReasonCode.PERMISSION_MISSING -> R.string.history_reason_permission_missing
        ReasonCode.SHIZUKU_NOT_RUNNING -> R.string.history_reason_shizuku_not_running
        ReasonCode.SHIZUKU_PERMISSION_MISSING -> R.string.history_reason_shizuku_permission_missing
        ReasonCode.EXECUTION_REVOKED -> R.string.history_reason_execution_revoked
        ReasonCode.CANCELLED -> R.string.history_reason_cancelled
        ReasonCode.TARGET_APP_UNAVAILABLE -> R.string.history_reason_target_app_unavailable
        ReasonCode.INVALID_INPUT -> R.string.history_reason_invalid_input
        ReasonCode.HANDLER_UNAVAILABLE -> R.string.history_reason_handler_unavailable
        ReasonCode.SYSTEM_COMMAND_FAILED -> R.string.history_reason_system_command_failed
        ReasonCode.STATE_UNAVAILABLE -> R.string.history_reason_state_unavailable
        ReasonCode.STATE_MISMATCH -> R.string.history_reason_state_mismatch
        ReasonCode.VOLUME_BELOW_MINIMUM -> R.string.history_reason_volume_below_minimum
        ReasonCode.MEDIA_SESSION_UNAVAILABLE -> R.string.history_reason_media_session_unavailable
        ReasonCode.NETWORK_TIMEOUT -> R.string.history_reason_network_timeout
        ReasonCode.NETWORK_FAILURE -> R.string.history_reason_network_failure
        ReasonCode.HTTP_ERROR -> R.string.history_reason_http_error
        ReasonCode.INVALID_LINK -> R.string.history_reason_invalid_link
        ReasonCode.UNEXPECTED_EXCEPTION -> R.string.history_reason_unexpected_exception
        ReasonCode.NO_EXECUTOR -> R.string.history_reason_no_executor
        ReasonCode.FAILED_BEFORE_DISPATCH -> R.string.history_reason_failed_before_dispatch
        ReasonCode.REQUEST_UNVERIFIED -> R.string.history_reason_request_unverified
        ReasonCode.UNKNOWN -> R.string.history_reason_unknown
    }

val ReasonCode.fixRes: Int
    get() = when (this) {
        ReasonCode.JSON_PARSE_ERROR -> R.string.history_fix_json_parse_error
        ReasonCode.JSON_INPUT_TOO_LARGE -> R.string.history_fix_json_input_too_large
        ReasonCode.JSON_INVALID_SCHEMA -> R.string.history_fix_json_invalid_schema
        ReasonCode.JSON_UNSUPPORTED_VERSION -> R.string.history_fix_json_unsupported_version
        ReasonCode.JSON_UNSUPPORTED_ACTION -> R.string.history_fix_json_unsupported_action
        ReasonCode.JSON_INVALID_PARAMETERS -> R.string.history_fix_json_invalid_parameters
        ReasonCode.JSON_DISPATCH_FAILED -> R.string.history_fix_json_dispatch_failed
        ReasonCode.PERMISSION_MISSING -> R.string.history_fix_permission_missing
        ReasonCode.SHIZUKU_NOT_RUNNING -> R.string.history_fix_shizuku_not_running
        ReasonCode.SHIZUKU_PERMISSION_MISSING -> R.string.history_fix_shizuku_permission_missing
        ReasonCode.EXECUTION_REVOKED -> R.string.history_fix_execution_revoked
        ReasonCode.CANCELLED -> R.string.history_fix_cancelled
        ReasonCode.TARGET_APP_UNAVAILABLE -> R.string.history_fix_target_app_unavailable
        ReasonCode.INVALID_INPUT -> R.string.history_fix_invalid_input
        ReasonCode.HANDLER_UNAVAILABLE -> R.string.history_fix_handler_unavailable
        ReasonCode.SYSTEM_COMMAND_FAILED -> R.string.history_fix_system_command_failed
        ReasonCode.STATE_UNAVAILABLE -> R.string.history_fix_state_unavailable
        ReasonCode.STATE_MISMATCH -> R.string.history_fix_state_mismatch
        ReasonCode.VOLUME_BELOW_MINIMUM -> R.string.history_fix_volume_below_minimum
        ReasonCode.MEDIA_SESSION_UNAVAILABLE -> R.string.history_fix_media_session_unavailable
        ReasonCode.NETWORK_TIMEOUT -> R.string.history_fix_network_timeout
        ReasonCode.NETWORK_FAILURE -> R.string.history_fix_network_failure
        ReasonCode.HTTP_ERROR -> R.string.history_fix_http_error
        ReasonCode.INVALID_LINK -> R.string.history_fix_invalid_link
        ReasonCode.UNEXPECTED_EXCEPTION -> R.string.history_fix_unexpected_exception
        ReasonCode.NO_EXECUTOR -> R.string.history_fix_no_executor
        ReasonCode.FAILED_BEFORE_DISPATCH -> R.string.history_fix_failed_before_dispatch
        ReasonCode.REQUEST_UNVERIFIED -> R.string.history_fix_request_unverified
        ReasonCode.UNKNOWN -> R.string.history_fix_unknown
    }
