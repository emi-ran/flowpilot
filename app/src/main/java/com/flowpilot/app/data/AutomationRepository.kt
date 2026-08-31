package com.flowpilot.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowpilot.app.data.model.Automation
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "automations")

/** Persists automation rules as JSON in a single DataStore preferences key. */
class AutomationRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val listSerializer = ListSerializer(Automation.serializer())

    private val key = stringPreferencesKey("rules")
    private val engineKey = androidx.datastore.preferences.core.booleanPreferencesKey("engine_enabled")

    val isEngineEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[engineKey] ?: true
    }

    suspend fun setEngineEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[engineKey] = enabled
        }
    }

    val automations: Flow<List<Automation>> = context.dataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            try {
                json.decodeFromString(listSerializer, raw)
            } catch (_: Exception) {
                emptyList()
            }
        } ?: emptyList()
    }

    suspend fun add(
        name: String,
        triggerEvent: com.flowpilot.app.data.model.TriggerEvent,
        appPackage: String,
        appName: String,
        actions: List<com.flowpilot.app.data.model.ActionType>,
        scheduledMinute: Int = 0,
        scheduledDays: Set<Int> = emptySet(),
    ): Automation {
        val primaryAction = actions.firstOrNull() ?: com.flowpilot.app.data.model.ActionType.NFC_ON
        val summary = actions.joinToString(" + ") { it.label }
        val rule = Automation(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank {
                if (triggerEvent == com.flowpilot.app.data.model.TriggerEvent.TIME_SCHEDULE) {
                    "Schedule %02d:%02d · %s".format(scheduledMinute / 60, scheduledMinute % 60, summary)
                } else {
                    "${appName.ifBlank { appPackage }} · $summary"
                }
            },
            triggerEvent = triggerEvent,
            appPackage = appPackage,
            appName = appName,
            scheduledMinute = scheduledMinute,
            scheduledDays = scheduledDays,
            action = primaryAction,
            actions = actions,
            createdAt = System.currentTimeMillis(),
        )
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: emptyList()
            prefs[key] = json.encodeToString(listSerializer, current + rule)
        }
        return rule
    }

    suspend fun update(rule: Automation) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: emptyList()
            val updated = current.map { if (it.id == rule.id) rule else it }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
    }

    suspend fun patchLastTriggeredAt(id: String, at: Long) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.map { if (it.id == id) it.copy(lastTriggeredAt = at) else it }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            val updated = current.map { if (it.id == id) it.copy(enabled = enabled) else it }
            prefs[key] = json.encodeToString(listSerializer, updated)
        }
    }

    suspend fun delete(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            prefs[key] = json.encodeToString(listSerializer, current.filterNot { it.id == id })
        }
    }

    suspend fun deleteMany(ids: Set<String>) {
        if (ids.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.let { safeDecode(it) } ?: return@edit
            prefs[key] = json.encodeToString(listSerializer, current.filterNot { it.id in ids })
        }
    }

    private fun safeDecode(raw: String): List<Automation> = try {
        json.decodeFromString(listSerializer, raw)
    } catch (_: Exception) {
        emptyList()
    }
}
