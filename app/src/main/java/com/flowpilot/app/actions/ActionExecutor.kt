package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.VibrationPattern
import com.flowpilot.app.data.model.SoundPreset
import kotlinx.serialization.Serializable

/** Result of attempting to execute an action. Kept honest — never claims success on a no-op. */
data class ActionResult(
    val success: Boolean,
    val message: String,
    val resultCode: ActionResultCode? = null,
    val resultArgs: List<String> = emptyList(),
    val diagnostic: ActionDiagnostic? = null,
)

/** Payload-free diagnostic metadata. Lengths are capped to avoid retaining unbounded input data. */
@Serializable
data class ActionDiagnostic(
    val reason: ActionDiagnosticReason,
    val expectedLength: Int? = null,
    val observedLength: Int? = null,
) {
    init {
        require(expectedLength == null || expectedLength in 0..1000)
        require(observedLength == null || observedLength in 0..1000)
    }
}

@Serializable
enum class ActionDiagnosticReason {
    MALFORMED_LINK,
    INVALID_LINK_COMPONENTS,
    UNSUPPORTED_LINK_HOST,
    INVALID_LINK_PATH,
    MISSING_VIDEO_ID,
    MULTIPLE_VIDEO_IDS,
    INVALID_VIDEO_ID_LENGTH,
    INVALID_VIDEO_ID_CHARACTERS,
}

/** Locale-neutral outcome metadata persisted by execution history. */
@Serializable
enum class ActionResultCode {
    COMPLETED,
    STATE_ENABLED,
    STATE_DISABLED,
    NOTIFICATION_POSTED,
    SMS_SENT,
    PERMISSION_REQUIRED,
    EXECUTION_CANCELLED,
}

data class ActionParameters(
    val notificationTitle: String = "",
    val notificationBody: String = "",
    val vibrationPattern: VibrationPattern = VibrationPattern.PULSE,
    val vibrationDurationMs: Int = 220,
    val vibrationAmplitude: Int = 180,
    val mediaVolumePercent: Int = 50,
    val mediaTargetPackage: String = "",
    val musicAppPackage: String = "",
    val musicQuery: String = "",
    val musicLink: String = "",
    val soundPreset: SoundPreset = SoundPreset.NOTIFICATION,
    val soundUri: String = "",
    val soundDurationMs: Int = 3_000,
    val launchPackage: String = "",
    val url: String = "",
    val ttsText: String = "",
    val ttsVoiceName: String = "",
    val ttsSpeechRate: Float = 1.0f,
    val ttsAudioFileName: String = "",
    /**
     * Set by [ActionParametersFactory] when the TTS template cannot produce speakable text.
     * The message is intentionally generic and never contains rendered template content.
     */
    val ttsTemplateError: String? = null,
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val alarmMessage: String = "",
    val timerDurationSeconds: Int = 300,
    val timerMessage: String = "",
    val webhookMethod: String = "POST",
    val webhookUrl: String = "",
    val webhookHeaders: String = "",
    val webhookBody: String = "",
    val webhookTimeoutSeconds: Int = 10,
    val phoneNumber: String = "",
    val screenBrightnessPercent: Int = 50,
    val forceStopPackage: String = "",
    val smsRecipient: String = "",
    val smsMessage: String = "",
)

/** A capability-aware executor for one family of system actions. */
interface ActionExecutor {
    val supportedTypes: Set<ActionType>
    fun execute(action: ActionType, parameters: ActionParameters = ActionParameters()): ActionResult

    /** Executors that need to suspend (synthesis, IO) override this; the default stays synchronous. */
    suspend fun executeAsync(action: ActionType, parameters: ActionParameters): ActionResult =
        execute(action, parameters)
}
