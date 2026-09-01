package com.flowpilot.app.actions

import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.VibrationPattern
import com.flowpilot.app.data.model.SoundPreset

/** Result of attempting to execute an action. Kept honest — never claims success on a no-op. */
data class ActionResult(
    val success: Boolean,
    val message: String,
)

data class ActionParameters(
    val notificationTitle: String = "",
    val notificationBody: String = "",
    val vibrationPattern: VibrationPattern = VibrationPattern.PULSE,
    val vibrationDurationMs: Int = 220,
    val vibrationAmplitude: Int = 180,
    val mediaVolumePercent: Int = 50,
    val soundPreset: SoundPreset = SoundPreset.NOTIFICATION,
    val soundUri: String = "",
    val soundDurationMs: Int = 3_000,
    val launchPackage: String = "",
    val url: String = "",
    val ttsText: String = "",
    val ttsVoiceName: String = "",
    val ttsSpeechRate: Float = 1.0f,
    val ttsAudioFileName: String = "",
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
)

/** A capability-aware executor for one family of system actions. */
interface ActionExecutor {
    val supportedTypes: Set<ActionType>
    fun execute(action: ActionType, parameters: ActionParameters = ActionParameters()): ActionResult
}
