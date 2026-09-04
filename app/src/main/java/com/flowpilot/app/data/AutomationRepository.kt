package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.security.SecretCipher
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "automations")

/** Persists automation rules as JSON in a single DataStore preferences key. */
class AutomationRepository(private val context: Context) {

    internal val rawDataStore: DataStore<Preferences>
        get() = context.dataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val listSerializer = ListSerializer(Automation.serializer())
    private val historySerializer = ListSerializer(ExecutionHistoryEntry.serializer())

    private val key = stringPreferencesKey("rules")
    private val historyKey = stringPreferencesKey("execution_history")
    private val engineKey = androidx.datastore.preferences.core.booleanPreferencesKey("engine_enabled")
    private val languageKey = stringPreferencesKey("app_language")
    private val themeKey = stringPreferencesKey("app_theme")

    val appLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[languageKey] ?: "system"
    }

    suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { prefs ->
            prefs[languageKey] = language
        }
    }

    val appTheme: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[themeKey] ?: "system"
    }

    suspend fun setAppTheme(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = theme
        }
    }

    val isEngineEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[engineKey] ?: true
    }

    suspend fun setEngineEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[engineKey] = enabled
        }
        notifyWidgets()
    }

    val automations: Flow<List<Automation>> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            val list = safeDecode(raw)
            list.map { it.withDecryptedSecrets() }
        } ?: emptyList()
    }

    val executionHistory: Flow<List<ExecutionHistoryEntry>> = context.dataStore.data.map { prefs ->
        prefs[historyKey]?.let { raw ->
            safeDecodeHistory(raw)
        } ?: emptyList()
    }

    suspend fun appendHistory(entry: ExecutionHistoryEntry) {
        context.dataStore.edit { prefs ->
            val current = prefs[historyKey]?.let { safeDecodeHistory(it) } ?: emptyList()
            val updated = (listOf(entry) + current).take(MAX_HISTORY_ENTRIES)
            prefs[historyKey] = json.encodeToString(historySerializer, updated)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(historyKey)
        }
    }

    suspend fun add(
        name: String,
        triggerEvent: com.flowpilot.app.data.model.TriggerEvent,
        appPackage: String,
        appName: String,
        actions: List<com.flowpilot.app.data.model.ActionType>,
        actionDelays: List<Int> = emptyList(),
        cooldownMinutes: Int = 0,
        flipScreenOffDetection: Boolean = false,
        scheduledMinute: Int = 0,
        scheduledDays: Set<Int> = emptySet(),
        batteryLevel: Int = 50,
        wifiSsid: String = "",
        bluetoothDeviceAddress: String = "",
        bluetoothDeviceName: String = "",
        nfcTagId: String = "",
        notificationAppPackage: String = "",
        notificationAppName: String = "",
        notificationKeyword: String = "",
        conditions: List<com.flowpilot.app.data.model.RuleCondition> = emptyList(),
        notificationTitle: String = "FlowPilot",
        notificationBody: String = "Automation ran",
        vibrationPattern: com.flowpilot.app.data.model.VibrationPattern = com.flowpilot.app.data.model.VibrationPattern.PULSE,
        vibrationDurationMs: Int = 220,
        vibrationAmplitude: Int = 180,
        mediaVolumePercent: Int = 50,
        soundPreset: com.flowpilot.app.data.model.SoundPreset = com.flowpilot.app.data.model.SoundPreset.NOTIFICATION,
        soundUri: String = "",
        soundName: String = "",
        soundDurationMs: Int = 3_000,
        launchPackage: String = "",
        launchAppName: String = "",
        url: String = "",
        ttsText: String = "",
        ttsVoiceName: String = "",
        ttsSpeechRate: Float = 1.0f,
        ttsAudioFileName: String = "",
        alarmHour: Int = 7,
        alarmMinute: Int = 0,
        alarmMessage: String = "",
        timerDurationSeconds: Int = 300,
        timerMessage: String = "",
        webhookMethod: String = "POST",
        webhookUrl: String = "",
        webhookHeaders: String = "",
        webhookBody: String = "",
        webhookTimeoutSeconds: Int = 10,
        phoneNumber: String = "",
        lightLux: Int = 10,
        screenBrightnessPercent: Int = 50,
        forceStopPackage: String = "",
        forceStopAppName: String = "",
        smsSenderFilter: String = "",
        smsMatchMode: com.flowpilot.app.data.model.SmsMatchMode = com.flowpilot.app.data.model.SmsMatchMode.CONTAINS,
        smsKeyword: String = "",
        smsRecipient: String = "",
        smsMessage: String = "",
        id: String = UUID.randomUUID().toString(),
    ): Automation {
        val primaryAction = actions.firstOrNull() ?: com.flowpilot.app.data.model.ActionType.NFC_ON
        val summary = actions.joinToString(" + ") { it.label }
        val rule = Automation(
            id = id,
            name = name.ifBlank {
                when (triggerEvent) {
                    com.flowpilot.app.data.model.TriggerEvent.TIME_SCHEDULE ->
                        "Schedule %02d:%02d · %s".format(scheduledMinute / 60, scheduledMinute % 60, summary)
                    com.flowpilot.app.data.model.TriggerEvent.CHARGER_CONNECTED,
                    com.flowpilot.app.data.model.TriggerEvent.CHARGER_DISCONNECTED ->
                        "${triggerEvent.label} · $summary"
                    com.flowpilot.app.data.model.TriggerEvent.BATTERY_BELOW,
                    com.flowpilot.app.data.model.TriggerEvent.BATTERY_ABOVE ->
                        "${triggerEvent.label} ${batteryLevel}% · $summary"
                    com.flowpilot.app.data.model.TriggerEvent.WIFI_CONNECTED,
                    com.flowpilot.app.data.model.TriggerEvent.WIFI_DISCONNECTED ->
                        "${triggerEvent.label} ${wifiSsid.ifBlank { "Any Wi-Fi" }} · $summary"
                    com.flowpilot.app.data.model.TriggerEvent.BLUETOOTH_CONNECTED,
                    com.flowpilot.app.data.model.TriggerEvent.BLUETOOTH_DISCONNECTED ->
                        "${triggerEvent.label} ${bluetoothDeviceName.ifBlank { bluetoothDeviceAddress }} · $summary"
                    com.flowpilot.app.data.model.TriggerEvent.NFC_TAG_SCANNED ->
                        "NFC Tag ($nfcTagId) · $summary"
                    com.flowpilot.app.data.model.TriggerEvent.NOTIFICATION_RECEIVED ->
                        "Notification (${notificationAppName.ifBlank { notificationAppPackage }}) · $summary"
                    com.flowpilot.app.data.model.TriggerEvent.CALL_RINGING,
                    com.flowpilot.app.data.model.TriggerEvent.CALL_ANSWERED,
                    com.flowpilot.app.data.model.TriggerEvent.CALL_OUTGOING,
                    com.flowpilot.app.data.model.TriggerEvent.CALL_ENDED,
                    com.flowpilot.app.data.model.TriggerEvent.DEVICE_FLIPPED_DOWN,
                    com.flowpilot.app.data.model.TriggerEvent.DEVICE_FLIPPED_UP,
                    com.flowpilot.app.data.model.TriggerEvent.DEVICE_SHAKE,
                    com.flowpilot.app.data.model.TriggerEvent.DEVICE_UNLOCKED ->
                        "${triggerEvent.label} · $summary"
                    com.flowpilot.app.data.model.TriggerEvent.LIGHT_BELOW,
                    com.flowpilot.app.data.model.TriggerEvent.LIGHT_ABOVE ->
                        "${triggerEvent.label} ${lightLux}lx · $summary"
                    com.flowpilot.app.data.model.TriggerEvent.SMS_RECEIVED ->
                        if (smsSenderFilter.isNotBlank()) "SMS from $smsSenderFilter · $summary" else "SMS Received · $summary"
                    else -> "${appName.ifBlank { appPackage }} · $summary"
                }
            },
            triggerEvent = triggerEvent,
            appPackage = appPackage,
            appName = appName,
            scheduledMinute = scheduledMinute,
            scheduledDays = scheduledDays,
            batteryLevel = batteryLevel,
            wifiSsid = wifiSsid,
            bluetoothDeviceAddress = bluetoothDeviceAddress,
            bluetoothDeviceName = bluetoothDeviceName,
            nfcTagId = nfcTagId,
            notificationAppPackage = notificationAppPackage,
            notificationAppName = notificationAppName,
            notificationKeyword = notificationKeyword,
            conditions = conditions,
            notificationTitle = notificationTitle,
            notificationBody = notificationBody,
            vibrationPattern = vibrationPattern,
            vibrationDurationMs = vibrationDurationMs,
            vibrationAmplitude = vibrationAmplitude,
            mediaVolumePercent = mediaVolumePercent,
            soundPreset = soundPreset,
            soundUri = soundUri,
            soundName = soundName,
            soundDurationMs = soundDurationMs,
            launchPackage = launchPackage,
            launchAppName = launchAppName,
            url = url,
            ttsText = ttsText,
            ttsVoiceName = ttsVoiceName,
            ttsSpeechRate = ttsSpeechRate,
            ttsAudioFileName = ttsAudioFileName,
            alarmHour = alarmHour,
            alarmMinute = alarmMinute,
            alarmMessage = alarmMessage,
            timerDurationSeconds = timerDurationSeconds,
            timerMessage = timerMessage,
            webhookMethod = webhookMethod,
            webhookUrl = webhookUrl,
            webhookHeaders = webhookHeaders,
            webhookBody = webhookBody,
            webhookTimeoutSeconds = webhookTimeoutSeconds,
            phoneNumber = phoneNumber,
            smsSenderFilter = smsSenderFilter,
            smsMatchMode = smsMatchMode,
            smsKeyword = smsKeyword,
            smsRecipient = smsRecipient,
            smsMessage = smsMessage,
            lightLux = lightLux,
            screenBrightnessPercent = screenBrightnessPercent,
            forceStopPackage = forceStopPackage,
            forceStopAppName = forceStopAppName,
            action = primaryAction,
            actions = actions,
            actionDelays = actionDelays,
            cooldownMinutes = cooldownMinutes,
            flipScreenOffDetection = flipScreenOffDetection,
            createdAt = System.currentTimeMillis(),
        )
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: emptyList()
            val encryptedRule = rule.withEncryptedSecrets()
            val updated = current.map { it.withEncryptedSecrets() } + encryptedRule
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
        return rule
    }

    suspend fun update(rule: Automation) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: emptyList()
            val encryptedRule = rule.withEncryptedSecrets()
            val updated = current.map {
                if (it.id == rule.id) encryptedRule else it.withEncryptedSecrets()
            }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
    }

    suspend fun patchLastTriggeredAt(id: String, at: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.map {
                val base = if (it.id == id) it.copy(lastTriggeredAt = at) else it
                base.withEncryptedSecrets()
            }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.map {
                val base = if (it.id == id) it.copy(enabled = enabled) else it
                base.withEncryptedSecrets()
            }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        notifyWidgets()
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.filterNot { it.id == id }.map { it.withEncryptedSecrets() }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
    }

    suspend fun deleteMany(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.filterNot { it.id in ids }.map { it.withEncryptedSecrets() }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
    }

    suspend fun importAutomations(
        imported: List<Automation>,
        strategy: com.flowpilot.app.data.backup.ImportStrategy,
    ): Int {
        if (imported.isEmpty()) return 0
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: emptyList()
            val finalRules = when (strategy) {
                com.flowpilot.app.data.backup.ImportStrategy.MERGE -> {
                    val remapped = imported.map { rule ->
                        rule.copy(
                            id = UUID.randomUUID().toString(),
                            createdAt = System.currentTimeMillis(),
                        ).withEncryptedSecrets()
                    }
                    current.map { it.withEncryptedSecrets() } + remapped
                }
                com.flowpilot.app.data.backup.ImportStrategy.REPLACE_ALL -> {
                    imported.map { it.withEncryptedSecrets() }
                }
            }
            prefs[key] = json.encodeToString(listSerializer, finalRules)
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
        return imported.size
    }

    suspend fun replaceAll(rules: List<Automation>) {
        context.dataStore.edit { prefs ->
            val encrypted = rules.map { it.withEncryptedSecrets() }
            prefs[key] = json.encodeToString(listSerializer, encrypted)
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
    }

    private fun notifyWidgets() {
        try {
            com.flowpilot.app.widget.FlowPilotWidgetProvider.updateAllWidgets(context)
        } catch (_: Throwable) {}
    }

    private fun safeDecode(raw: String): List<Automation> = try {
        json.decodeFromString(listSerializer, raw)
    } catch (_: Exception) {
        emptyList()
    }

    private fun safeDecodeHistory(raw: String): List<ExecutionHistoryEntry> = try {
        json.decodeFromString(historySerializer, raw)
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        const val MAX_HISTORY_ENTRIES = 100
    }

    suspend fun migrateLegacySecretsIfNeeded() {
        context.dataStore.edit { prefs ->
            val raw = prefs[key] ?: return@edit
            val list = safeDecode(raw)
            val hasPlaintext = list.any { rule ->
                (rule.webhookUrl.isNotEmpty() && !SecretCipher.isEncrypted(rule.webhookUrl)) ||
                (rule.webhookHeaders.isNotEmpty() && !SecretCipher.isEncrypted(rule.webhookHeaders)) ||
                (rule.webhookBody.isNotEmpty() && !SecretCipher.isEncrypted(rule.webhookBody))
            }
            if (hasPlaintext) {
                val encryptedList = list.map { it.withEncryptedSecrets() }
                prefs[key] = json.encodeToString(listSerializer, encryptedList)
            }
        }
    }

    private suspend fun cleanupOrphanTtsFiles() {
        try {
            val rules = automations.first()
            val usedFiles = rules.mapNotNull { it.ttsAudioFileName.takeIf { f -> f.isNotBlank() } }.toSet()
            com.flowpilot.app.actions.TtsManager(context).cleanStaleFiles(usedFiles)
        } catch (_: Throwable) {}
    }
}
