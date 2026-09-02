package com.flowpilot.app.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.engine.PhoneNumberUtils

/**
 * Handles phone actions: opening the system dialer, preparing numbers in the dialer,
 * and initiating direct phone calls when CALL_PHONE permission is held.
 *
 * Enforces privacy: raw phone numbers are never included in logcat, action results, or error messages.
 */
class PhoneExecutor(
    private val context: Context,
    private val resolveActivity: (Intent) -> Boolean = { intent ->
        intent.resolveActivity(context.packageManager) != null
    },
    private val startActivity: (Intent) -> Unit = { intent -> context.startActivity(intent) },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.OPEN_DIALER,
        ActionType.DIAL_NUMBER,
        ActionType.CALL_NUMBER,
    )

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult = when (action) {
        ActionType.OPEN_DIALER -> openDialer()
        ActionType.DIAL_NUMBER -> dialNumber(parameters.phoneNumber)
        ActionType.CALL_NUMBER -> callNumber(parameters.phoneNumber)
        else -> ActionResult(false, "Unsupported action: ${action.label}")
    }

    private fun openDialer(): ActionResult {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            if (!resolveActivity(intent)) {
                Log.w(TAG, "No dialer application resolved")
                return ActionResult(false, "No dialer application found")
            }
            startActivity(intent)
            Log.i(TAG, "Dialer opened successfully")
            ActionResult(true, "Dialer opened")
        } catch (_: Exception) {
            Log.w(TAG, "Failed to open dialer")
            ActionResult(false, "Failed to open dialer")
        }
    }

    private fun dialNumber(phoneNumber: String): ActionResult {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        if (normalized.isBlank()) {
            Log.w(TAG, "Dial number failed: empty or invalid number")
            return ActionResult(false, "Invalid or empty phone number")
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            if (!resolveActivity(intent)) {
                Log.w(TAG, "No dialer application resolved for dial intent")
                return ActionResult(false, "No dialer application found")
            }
            startActivity(intent)
            Log.i(TAG, "Phone number prepared in dialer")
            ActionResult(true, "Phone number prepared in dialer")
        } catch (_: Exception) {
            Log.w(TAG, "Failed to prepare number in dialer")
            ActionResult(false, "Failed to prepare number in dialer")
        }
    }

    private fun callNumber(phoneNumber: String): ActionResult {
        val normalized = PhoneNumberUtils.normalize(phoneNumber)
        if (normalized.isBlank()) {
            Log.w(TAG, "Direct call failed: empty or invalid number")
            return ActionResult(false, "Invalid or empty phone number")
        }

        val hasCallPermission = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (!hasCallPermission) {
            Log.w(TAG, "Direct call blocked: CALL_PHONE permission not granted")
            return ActionResult(false, "Phone call permission required")
        }

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$normalized")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            if (!resolveActivity(intent)) {
                Log.w(TAG, "No phone calling application resolved for call intent")
                return ActionResult(false, "No phone calling application found")
            }
            startActivity(intent)
            Log.i(TAG, "Direct phone call initiated")
            ActionResult(true, "Direct phone call initiated")
        } catch (_: Exception) {
            Log.w(TAG, "Failed to initiate direct phone call")
            ActionResult(false, "Failed to initiate phone call")
        }
    }

    private companion object {
        const val TAG = "FlowPilotPhone"
    }
}
