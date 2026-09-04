package com.flowpilot.app.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.engine.PhoneNumberUtils

/**
 * Handles SMS actions: directly sending SMS via SmsManager when SEND_SMS permission is held,
 * and preparing SMS drafts in the default SMS application.
 *
 * Supports dynamic template variables in recipient and message (${sms.sender}, ${sms.body}, ${location.lat}, etc.).
 * Enforces privacy: raw phone numbers are never included in logcat or user notifications.
 */
class SmsExecutor(
    private val context: Context,
    private val resolveActivity: (Intent) -> Boolean = { intent ->
        intent.resolveActivity(context.packageManager) != null
    },
    private val startActivity: (Intent) -> Unit = { intent -> context.startActivity(intent) },
    private val smsManagerProvider: () -> SmsManager? = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    },
    private val sendTextMessage: (recipient: String, message: String) -> Unit = { recipient, message ->
        val smsManager = smsManagerProvider() ?: error("SmsManager unavailable")
        val parts = smsManager.divideMessage(message)
        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
        } else {
            smsManager.sendTextMessage(recipient, null, message, null, null)
        }
    },
) : ActionExecutor {

    override val supportedTypes: Set<ActionType> = setOf(
        ActionType.SEND_SMS,
        ActionType.DRAFT_SMS,
    )

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult = when (action) {
        ActionType.SEND_SMS -> sendSms(parameters)
        ActionType.DRAFT_SMS -> draftSms(parameters)
        else -> ActionResult(false, "Unsupported action: ${action.label}")
    }

    private fun resolveRecipient(parameters: ActionParameters): String {
        var recipient = WebhookTemplateRenderer.render(parameters.smsRecipient, parameters.webhookTemplateContext).trim()
        if (recipient.isBlank()) {
            recipient = parameters.webhookTemplateContext?.smsSender.orEmpty().trim()
        }
        return recipient
    }

    private fun resolveMessage(parameters: ActionParameters): String {
        return WebhookTemplateRenderer.render(parameters.smsMessage, parameters.webhookTemplateContext)
    }

    private fun sendSms(parameters: ActionParameters): ActionResult {
        val recipient = resolveRecipient(parameters)
        if (recipient.isBlank()) {
            Log.w(TAG, "Direct SMS failed: recipient is empty")
            return ActionResult(false, "Recipient phone number is required")
        }

        val message = resolveMessage(parameters)
        if (message.isBlank()) {
            Log.w(TAG, "Direct SMS failed: message body is empty")
            return ActionResult(false, "SMS message body cannot be empty")
        }

        val hasSendPermission = context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (!hasSendPermission) {
            Log.w(TAG, "Direct SMS blocked: SEND_SMS permission not granted")
            return ActionResult(false, "SEND_SMS permission required")
        }

        return try {
            sendTextMessage(recipient, message)
            val masked = PhoneNumberUtils.mask(recipient)
            Log.i(TAG, "Direct SMS sent successfully to $masked")
            ActionResult(true, "SMS sent to $masked")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send SMS: ${e.message}")
            ActionResult(false, "Failed to send SMS: ${e.message ?: "Unknown error"}")
        }
    }

    private fun draftSms(parameters: ActionParameters): ActionResult {
        val recipient = resolveRecipient(parameters)
        val message = resolveMessage(parameters)

        val uri = if (recipient.isNotBlank()) {
            Uri.parse("smsto:${Uri.encode(recipient)}")
        } else {
            Uri.parse("smsto:")
        }

        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (message.isNotBlank()) {
                putExtra("sms_body", message)
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return try {
            if (!resolveActivity(intent)) {
                Log.w(TAG, "No messaging application resolved for SMS draft intent")
                return ActionResult(false, "No messaging application found")
            }
            startActivity(intent)
            Log.i(TAG, "SMS draft opened successfully")
            ActionResult(true, "SMS draft opened")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to draft SMS: ${e.message}")
            ActionResult(false, "Failed to open SMS draft")
        }
    }

    companion object {
        private const val TAG = "SmsExecutor"
    }
}
