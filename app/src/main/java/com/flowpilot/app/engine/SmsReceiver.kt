package com.flowpilot.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * System BroadcastReceiver for android.provider.Telephony.SMS_RECEIVED.
 * Reassembles incoming multi-part SMS messages, extracts sender address and body,
 * and enqueues events into SmsEventTracker for rule evaluation.
 *
 * Security: Registered with android.permission.BROADCAST_SMS to ensure only the Android OS
 * telephony subsystem can dispatch broadcasts to this receiver.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val firstMessage = messages[0] ?: return
            val sender = firstMessage.displayOriginatingAddress
                ?: firstMessage.originatingAddress
                ?: ""
            val fullBody = messages.joinToString("") { msg ->
                msg.displayMessageBody ?: msg.messageBody.orEmpty()
            }
            val timestamp = if (firstMessage.timestampMillis > 0L) firstMessage.timestampMillis else System.currentTimeMillis()

            if (sender.isNotBlank() || fullBody.isNotBlank()) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val enqueued = AutomationService.enqueueSmsIfEngineEnabled(
                            context = context.applicationContext,
                            sender = sender,
                            body = fullBody,
                            timestamp = timestamp,
                        )
                        if (enqueued) {
                            val masked = PhoneNumberUtils.mask(sender)
                            Log.i(TAG, "Incoming SMS queued from $masked (len=${fullBody.length})")
                            AutomationService.reconcileEnabled(context.applicationContext)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error queuing incoming SMS: ${e.javaClass.simpleName}")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling incoming SMS intent: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
    }
}
