package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.ExecutionHistoryEntry
import com.flowpilot.app.data.security.SecretCipher
import com.flowpilot.app.engine.GeofenceTransition
import com.flowpilot.app.engine.GeofenceDiagnostic
import com.flowpilot.app.engine.GeofenceDiagnosticStatus
import com.flowpilot.app.engine.GeofenceEvent
import com.flowpilot.app.ui.util.automaticAutomationName
import com.flowpilot.app.ui.util.localizedForAppLanguage
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "automations")

/** Persists automation rules as JSON in a single DataStore preferences key. */
class AutomationRepository(private val context: Context) {

    data class ExecutionReservation(
        val rule: Automation,
        val revision: Long,
        val token: String,
        val previousLastTriggeredAt: Long,
    )

    internal val rawDataStore: DataStore<Preferences>
        get() = context.dataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val listSerializer = ListSerializer(Automation.serializer())
    private val historySerializer = ListSerializer(ExecutionHistoryEntry.serializer())
    private val geofenceTransitionListSerializer = ListSerializer(GeofenceTransition.serializer())
    private val geofenceDiagnosticListSerializer = ListSerializer(GeofenceDiagnostic.serializer())

    private val key = stringPreferencesKey("rules")
    private val executionRevisionKey = longPreferencesKey("execution_revision")
    private val historyKey = stringPreferencesKey("execution_history")
    private val geofenceQueueKey = stringPreferencesKey("geofence_event_queue")
    private val geofenceDiagnosticsKey = stringPreferencesKey("geofence_diagnostics")
    private val engineKey = androidx.datastore.preferences.core.booleanPreferencesKey("engine_enabled")
    private val languageKey = stringPreferencesKey("app_language")
    private val themeKey = stringPreferencesKey("app_theme")

    val appLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[languageKey] ?: "system"
    }

    val geofenceDiagnostics: Flow<Map<String, GeofenceDiagnostic>> = context.dataStore.data.map { prefs ->
        prefs[geofenceDiagnosticsKey]
            ?.let(::safeDecodeGeofenceDiagnostics)
            .orEmpty()
            .associateBy { it.automationId }
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

    suspend fun toggleEngineEnabled(): Boolean {
        val updated = context.dataStore.edit { prefs ->
            prefs[engineKey] = !(prefs[engineKey] ?: true)
        }
        notifyWidgets()
        return updated[engineKey] ?: true
    }

    val automations: Flow<List<Automation>> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            val list = safeDecode(raw)
            list.map { stored ->
                stored.withDecryptedSecrets().copy(name = stored.normalizedName)
            }
        } ?: emptyList()
    }

    val executionHistory: Flow<List<ExecutionHistoryEntry>> = context.dataStore.data.map { prefs ->
        val history = prefs[historyKey]?.let { safeDecodeHistory(it) }.orEmpty()
        if (history.any { it.ruleName != it.normalizedRuleName }) {
            val migrated = context.dataStore.edit { migrateHistory(it) }
            migrated[historyKey]?.let { safeDecodeHistory(it) }.orEmpty()
        } else {
            history
        }
    }

    suspend fun appendHistory(entry: ExecutionHistoryEntry) {
        context.dataStore.edit { prefs ->
            migrateHistory(prefs)
            val current = prefs[historyKey]?.let { safeDecodeHistory(it) } ?: emptyList()
            val updated = (listOf(entry.copy(ruleName = entry.normalizedRuleName)) + current).take(MAX_HISTORY_ENTRIES)
            prefs[historyKey] = json.encodeToString(historySerializer, updated)
        }
    }

    suspend fun clearHistory() {
        context.dataStore.edit { prefs ->
            prefs.remove(historyKey)
        }
    }

    suspend fun enqueueGeofenceEvents(events: List<GeofenceTransition>) {
        if (events.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[geofenceQueueKey]?.let { raw ->
                try {
                    json.decodeFromString(geofenceTransitionListSerializer, raw)
                } catch (_: Throwable) {
                    emptyList()
                }
            } ?: emptyList()
            // Retain up to 50 events to prevent unbounded storage
            val updated = (current + events).takeLast(50)
            prefs[geofenceQueueKey] = json.encodeToString(geofenceTransitionListSerializer, updated)
        }
    }

    suspend fun drainGeofenceEvents(): List<GeofenceTransition> {
        var drained: List<GeofenceTransition> = emptyList()
        context.dataStore.edit { prefs ->
            val raw = prefs[geofenceQueueKey]
            if (!raw.isNullOrBlank()) {
                drained = try {
                    json.decodeFromString(geofenceTransitionListSerializer, raw)
                } catch (_: Throwable) {
                    emptyList()
                }
                prefs.remove(geofenceQueueKey)
            }
        }
        return drained
    }

    suspend fun recordGeofenceRegistration(automationIds: List<String>, at: Long = System.currentTimeMillis()) {
        updateGeofenceDiagnostics(automationIds, clearReceiverError = true) { id, current ->
            current.copy(
                automationId = id,
                status = GeofenceDiagnosticStatus.REGISTERED,
                lastRegistrationAt = at,
                error = "",
                updatedAt = at,
            )
        }
    }

    suspend fun recordGeofenceUnregistration(automationIds: List<String>, at: Long = System.currentTimeMillis()) {
        updateGeofenceDiagnostics(automationIds) { id, current ->
            current.copy(
                automationId = id,
                status = GeofenceDiagnosticStatus.UNREGISTERED,
                error = "",
                updatedAt = at,
            )
        }
    }

    suspend fun recordGeofenceRegistrationFailure(
        automationIds: List<String>,
        error: String,
        at: Long = System.currentTimeMillis(),
    ) {
        updateGeofenceDiagnostics(automationIds) { id, current ->
            current.copy(
                automationId = id,
                status = GeofenceDiagnosticStatus.REGISTRATION_FAILED,
                error = error.take(MAX_GEOFENCE_ERROR_LENGTH),
                updatedAt = at,
            )
        }
    }

    suspend fun recordGeofenceTransitions(transitions: List<GeofenceTransition>) {
        if (transitions.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[geofenceDiagnosticsKey]
                ?.let(::safeDecodeGeofenceDiagnostics)
                .orEmpty()
                .associateBy { it.automationId }
                .toMutableMap()
            transitions.forEach { transition ->
                val prior = current[transition.automationId] ?: GeofenceDiagnostic(
                    automationId = transition.automationId,
                    status = GeofenceDiagnosticStatus.REGISTERED,
                )
                current[transition.automationId] = prior.copy(
                    status = when (transition.event) {
                        GeofenceEvent.ENTER -> GeofenceDiagnosticStatus.TRANSITION_ENTER
                        GeofenceEvent.EXIT -> GeofenceDiagnosticStatus.TRANSITION_EXIT
                    },
                    lastTransitionAt = transition.timestamp,
                    lastTransition = transition.event,
                    error = "",
                    updatedAt = transition.timestamp,
                )
            }
            current.remove(GEOFENCE_RECEIVER_DIAGNOSTIC_ID)
            prefs[geofenceDiagnosticsKey] = json.encodeToString(
                geofenceDiagnosticListSerializer,
                current.values.toList(),
            )
        }
    }

    suspend fun clearGeofenceReceiverError() {
        context.dataStore.edit { prefs ->
            val current = prefs[geofenceDiagnosticsKey]
                ?.let(::safeDecodeGeofenceDiagnostics)
                .orEmpty()
                .associateBy { it.automationId }
                .toMutableMap()
            if (current.remove(GEOFENCE_RECEIVER_DIAGNOSTIC_ID) != null) {
                prefs[geofenceDiagnosticsKey] = json.encodeToString(
                    geofenceDiagnosticListSerializer,
                    current.values.toList(),
                )
            }
        }
    }

    suspend fun recordGeofenceReceiverError(error: String, at: Long = System.currentTimeMillis()) {
        updateGeofenceDiagnostics(listOf(GEOFENCE_RECEIVER_DIAGNOSTIC_ID)) { id, current ->
            current.copy(
                automationId = id,
                status = GeofenceDiagnosticStatus.RECEIVER_ERROR,
                error = error.take(MAX_GEOFENCE_ERROR_LENGTH),
                updatedAt = at,
            )
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
        geofenceName: String = "",
        geofenceLatitude: Double = 0.0,
        geofenceLongitude: Double = 0.0,
        geofenceRadiusMeters: Int = 150,
        id: String = UUID.randomUUID().toString(),
    ): Automation {
        val primaryAction = actions.firstOrNull() ?: com.flowpilot.app.data.model.ActionType.NFC_ON
        val automaticName = automaticAutomationName(
            context = context.localizedForAppLanguage(appLanguage.first()),
            trigger = triggerEvent,
            actions = actions,
            appName = appName,
            appPackage = appPackage,
            scheduledMinute = scheduledMinute,
            batteryLevel = batteryLevel,
            wifiSsid = wifiSsid,
            bluetoothDeviceName = bluetoothDeviceName,
            bluetoothDeviceAddress = bluetoothDeviceAddress,
            nfcTagId = nfcTagId,
            notificationAppName = notificationAppName,
            notificationAppPackage = notificationAppPackage,
            lightLux = lightLux,
            geofenceName = geofenceName,
            geofenceRadiusMeters = geofenceRadiusMeters,
        )
        var rule = Automation(
            id = id,
            name = name.ifBlank { automaticName },
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
            geofenceName = geofenceName,
            geofenceLatitude = geofenceLatitude,
            geofenceLongitude = geofenceLongitude,
            geofenceRadiusMeters = geofenceRadiusMeters,
            action = primaryAction,
            actions = actions,
            actionDelays = actionDelays,
            cooldownMinutes = cooldownMinutes,
            flipScreenOffDetection = flipScreenOffDetection,
            createdAt = System.currentTimeMillis(),
        )
        executionStateMutex.withLock {
        context.dataStore.edit { prefs ->
            migrateHistory(prefs)
            val current = prefs[key]?.let { safeDecode(it) } ?: emptyList()
            rule = rule.copy(executionRevision = nextExecutionRevision(prefs))
            val encryptedRule = rule.withEncryptedSecrets()
            check(current.none { it.id == rule.id }) { "Duplicate automation ID" }
            val updated = current.map { it.withEncryptedSecrets() } + encryptedRule
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
        return rule
    }

    /** Clones a rule through decrypted domain data so encrypted fields get fresh IVs. */
    suspend fun duplicate(
        sourceId: String,
        copyName: String,
        newId: String = UUID.randomUUID().toString(),
        createdAt: Long = System.currentTimeMillis(),
    ): Automation? {
        var clone: Automation? = null
        var createdCloneTtsFile: java.io.File? = null
        try {
            executionStateMutex.withLock {
            context.dataStore.edit { prefs ->
                migrateHistory(prefs)
                val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
                val source = current.firstOrNull { it.id == sourceId }?.withDecryptedSecrets() ?: return@edit
                val ttsManager = com.flowpilot.app.actions.TtsManager(context)
                val cloneTtsFileName = source.ttsAudioFileName.takeIf { it.isNotBlank() }?.let { sourceFileName ->
                    val sourceFile = ttsManager.getCacheFile(sourceFileName)
                    val targetFileName = ttsManager.computeCacheFileName(
                        newId,
                        source.ttsText,
                        source.ttsVoiceName,
                        source.ttsSpeechRate,
                    )
                    val targetFile = ttsManager.getCacheFile(targetFileName)
                    if (sourceFile?.isFile != true || sourceFile.length() <= 0L || targetFile == null) {
                        throw java.io.IOException("Source TTS cache file is missing")
                    }
                    val targetExisted = targetFile.exists()
                    try {
                        sourceFile.copyTo(targetFile, overwrite = false)
                        createdCloneTtsFile = targetFile
                        targetFileName
                    } catch (error: java.io.IOException) {
                        if (!targetExisted) targetFile.delete()
                        throw error
                    }
                }.orEmpty()
                clone = source.copy(
                id = newId,
                    name = copyName,
                    enabled = false,
                    ttsAudioFileName = cloneTtsFileName,
                    createdAt = createdAt,
                    lastTriggeredAt = 0L,
                executionRevision = nextExecutionRevision(prefs),
            )
                check(current.none { it.id == newId }) { "Duplicate automation ID" }
                prefs[key] = json.encodeToString(
                    listSerializer,
                    current.map { it.withEncryptedSecrets() } + clone!!.withEncryptedSecrets(),
                )
            }
            }
        } catch (error: Throwable) {
            createdCloneTtsFile?.delete()
            throw error
        }
        if (clone != null) {
            cleanupOrphanTtsFiles()
            notifyWidgets()
        }
        return clone
    }

    suspend fun update(rule: Automation) {
        executionStateMutex.withLock {
        context.dataStore.edit { prefs ->
            migrateHistory(prefs)
            val current = prefs[key]?.let { safeDecode(it) } ?: emptyList()
            val revision = nextExecutionRevision(prefs)
            val updated = current.map {
                if (it.id == rule.id) {
                    revokePendingExecution(rule.copy(
                        name = rule.normalizedName,
                        executionRevision = revision,
                    )).withEncryptedSecrets()
                } else it.withEncryptedSecrets()
            }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
    }

    suspend fun patchLastTriggeredAt(id: String, at: Long) {
        context.dataStore.edit { prefs ->
            migrateHistory(prefs)
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.map {
                val base = if (it.id == id) it.copy(lastTriggeredAt = at) else it
                base.withEncryptedSecrets()
            }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
    }

    /** Atomically claims an automatic run before any action side effect. */
    suspend fun reserveExecution(
        id: String,
        expectedRevision: Long,
        at: Long = System.currentTimeMillis(),
    ): ExecutionReservation? {
        var reservation: ExecutionReservation? = null
        executionStateMutex.withLock {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val index = current.indexOfFirst { it.id == id }
            if (index < 0) return@edit
            val stored = current[index]
            val rule = stored.withDecryptedSecrets()
            if (!rule.enabled || rule.executionRevision != expectedRevision ||
                rule.isCoolingDown(at) || rule.executionLeaseExpiresAt > at
            ) {
                return@edit
            }
            val token = UUID.randomUUID().toString()
            val updated = current.toMutableList()
            updated[index] = stored.copy(
                lastTriggeredAt = at,
                executionLeaseToken = token,
                executionLeaseExpiresAt = at + EXECUTION_LEASE_MS,
            )
            prefs[key] = json.encodeToString(listSerializer, updated)
            reservation = ExecutionReservation(
                rule = rule.copy(
                    lastTriggeredAt = at,
                    executionLeaseToken = token,
                    executionLeaseExpiresAt = at + EXECUTION_LEASE_MS,
                ),
                revision = rule.executionRevision,
                token = token,
                previousLastTriggeredAt = rule.lastTriggeredAt,
            )
        }
        }
        return reservation
    }

    /** Releases a reservation only when its exact durable lease token still owns the rule. */
    suspend fun releaseExecutionReservation(reservation: ExecutionReservation, successful: Boolean) {
        executionStateMutex.withLock {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.map { stored ->
                if (stored.id == reservation.rule.id &&
                    stored.executionRevision == reservation.revision &&
                    stored.executionLeaseToken == reservation.token
                ) {
                    stored.copy(
                        lastTriggeredAt = if (successful) stored.lastTriggeredAt else reservation.previousLastTriggeredAt,
                        executionLeaseToken = "",
                        executionLeaseExpiresAt = 0L,
                    )
                } else stored
            }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        }
    }

    /** Extends a live lease before a bounded action delay; stale owners cannot renew. */
    suspend fun renewExecutionReservation(
        reservation: ExecutionReservation,
        at: Long = System.currentTimeMillis(),
    ): Boolean {
        var renewed = false
        executionStateMutex.withLock {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.map { stored ->
                if (stored.id == reservation.rule.id &&
                    stored.enabled &&
                    stored.executionRevision == reservation.revision &&
                    stored.executionLeaseToken == reservation.token
                ) {
                    renewed = true
                    stored.copy(executionLeaseExpiresAt = at + EXECUTION_LEASE_MS)
                } else stored
            }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        }
        return renewed
    }

    /** Valid only while rule still exists, is enabled, and has unchanged execution config. */
    suspend fun isExecutionAuthorized(id: String, revision: Long): Boolean =
        rawDataStore.data.first()[key]
            ?.let { safeDecode(it) }
            .orEmpty()
            .firstOrNull { it.id == id }
            ?.let { it.enabled && it.executionRevision == revision }
            ?: false

    suspend fun setEnabled(id: String, enabled: Boolean) {
        executionStateMutex.withLock {
        context.dataStore.edit { prefs ->
            migrateHistory(prefs)
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val revision = if (current.any { it.id == id && it.enabled != enabled }) {
                nextExecutionRevision(prefs)
            } else null
            val updated = current.map {
                val base = if (it.id == id && it.enabled != enabled) {
                    revokePendingExecution(it.copy(enabled = enabled, executionRevision = checkNotNull(revision)))
                } else it
                base.withEncryptedSecrets()
            }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        }
        notifyWidgets()
    }

    suspend fun delete(id: String) {
        executionStateMutex.withLock {
        context.dataStore.edit { prefs ->
            migrateHistory(prefs)
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.filterNot { it.id == id }.map { it.withEncryptedSecrets() }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
    }

    suspend fun deleteMany(ids: Set<String>) {
        if (ids.isEmpty()) return
        executionStateMutex.withLock {
        context.dataStore.edit { prefs ->
            migrateHistory(prefs)
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.filterNot { it.id in ids }.map { it.withEncryptedSecrets() }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
    }

    suspend fun importAutomations(
        imported: List<Automation>,
        strategy: com.flowpilot.app.data.backup.ImportStrategy,
    ): Int {
        if (imported.isEmpty()) return 0
        executionStateMutex.withLock {
        context.dataStore.edit { prefs ->
            migrateHistory(prefs)
            val current = prefs[key]?.let { safeDecode(it) } ?: emptyList()
            val finalRules = when (strategy) {
                com.flowpilot.app.data.backup.ImportStrategy.MERGE -> {
                    val remapped = imported.map { rule ->
                        rule.copy(
                            id = UUID.randomUUID().toString(),
                            createdAt = System.currentTimeMillis(),
                            name = rule.normalizedName,
                            executionRevision = nextExecutionRevision(prefs),
                        ).withEncryptedSecrets()
                    }
                    requireUniqueIds(current + remapped)
                    current.map { it.withEncryptedSecrets() } + remapped
                }
                com.flowpilot.app.data.backup.ImportStrategy.REPLACE_ALL -> {
                    requireUniqueIds(imported)
                    imported.map { rule ->
                        revokePendingExecution(rule.copy(
                            name = rule.normalizedName,
                            executionRevision = nextExecutionRevision(prefs),
                        )).withEncryptedSecrets()
                    }
                }
            }
            prefs[key] = json.encodeToString(listSerializer, finalRules)
        }
        }
        cleanupOrphanTtsFiles()
        notifyWidgets()
        return imported.size
    }

    suspend fun replaceAll(rules: List<Automation>) {
        requireUniqueIds(rules)
        executionStateMutex.withLock {
        context.dataStore.edit { prefs ->
            migrateHistory(prefs)
            val encrypted = rules.map { rule ->
                revokePendingExecution(rule.copy(
                    name = rule.normalizedName,
                    executionRevision = nextExecutionRevision(prefs),
                )).withEncryptedSecrets()
            }
            prefs[key] = json.encodeToString(listSerializer, encrypted)
        }
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
        recoverDuplicateIds(json.decodeFromString(listSerializer, raw))
    } catch (_: Exception) {
        emptyList()
    }

    private fun nextExecutionRevision(prefs: MutablePreferences): Long {
        val next = (prefs[executionRevisionKey] ?: 0L) + 1L
        prefs[executionRevisionKey] = next
        return next
    }

    private fun revokePendingExecution(rule: Automation): Automation = rule.copy(
        executionLeaseToken = "",
        executionLeaseExpiresAt = 0L,
    )

    private fun requireUniqueIds(rules: List<Automation>) {
        require(rules.map { it.id }.toSet().size == rules.size) { "Duplicate automation ID" }
    }

    private fun recoverDuplicateIds(rules: List<Automation>): List<Automation> =
        rules.distinctBy { it.id }

    /** Serializes in-process rule mutation with final authorization and dispatch. */
    suspend fun <T> dispatchIfAuthorized(
        reservation: ExecutionReservation,
        block: suspend () -> T,
    ): T? = executionStateMutex.withLock {
        val stored = rawDataStore.data.first()[key]
            ?.let(::safeDecode)
            ?.firstOrNull { it.id == reservation.rule.id }
        if (stored?.enabled == true &&
            stored.executionRevision == reservation.revision &&
            stored.executionLeaseToken == reservation.token &&
            stored.executionLeaseExpiresAt > System.currentTimeMillis()
        ) {
            block()
        } else null
    }

    private fun migrateHistory(prefs: MutablePreferences) {
        val raw = prefs[historyKey] ?: return
        val history = safeDecodeHistory(raw)
        val migrated = history.map { it.copy(ruleName = it.normalizedRuleName) }
        if (migrated != history) {
            prefs[historyKey] = json.encodeToString(historySerializer, migrated)
        }
    }

    private fun safeDecodeHistory(raw: String): List<ExecutionHistoryEntry> = try {
        json.decodeFromString(historySerializer, raw)
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun updateGeofenceDiagnostics(
        automationIds: List<String>,
        clearReceiverError: Boolean = false,
        update: (String, GeofenceDiagnostic) -> GeofenceDiagnostic,
    ) {
        if (automationIds.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[geofenceDiagnosticsKey]
                ?.let(::safeDecodeGeofenceDiagnostics)
                .orEmpty()
                .associateBy { it.automationId }
                .toMutableMap()
            automationIds.distinct().forEach { id ->
                val prior = current[id] ?: GeofenceDiagnostic(
                    automationId = id,
                    status = GeofenceDiagnosticStatus.UNREGISTERED,
                )
                current[id] = update(id, prior)
            }
            if (clearReceiverError) {
                current.remove(GEOFENCE_RECEIVER_DIAGNOSTIC_ID)
            }
            prefs[geofenceDiagnosticsKey] = json.encodeToString(
                geofenceDiagnosticListSerializer,
                current.values.toList(),
            )
        }
    }

    private fun safeDecodeGeofenceDiagnostics(raw: String): List<GeofenceDiagnostic> = try {
        json.decodeFromString(geofenceDiagnosticListSerializer, raw)
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        const val MAX_HISTORY_ENTRIES = 100
        const val GEOFENCE_RECEIVER_DIAGNOSTIC_ID = "__geofence_receiver__"
        private const val MAX_GEOFENCE_ERROR_LENGTH = 300
        private const val EXECUTION_LEASE_MS = 10 * 60_000L
        private val executionStateMutex = Mutex()
    }

    suspend fun migrateLegacySecretsIfNeeded() {
        context.dataStore.edit { prefs ->
            migrateHistory(prefs)
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
