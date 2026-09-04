package com.flowpilot.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.flowpilot.app.data.model.Automation
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class FlowPilotBackup(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val automations: List<Automation> = emptyList(),
) {
    companion object {
        const val BACKUP_VERSION = 1
    }
}

enum class ImportStrategy {
    MERGE,
    REPLACE_ALL,
}

object BackupManager {

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val parserJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToString(automations: List<Automation>): String {
        // Decrypt secrets so the JSON can be imported on other devices cleanly
        val plainRules = automations.map { it.withDecryptedSecrets() }
        val backup = FlowPilotBackup(
            version = FlowPilotBackup.BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            automations = plainRules,
        )
        return prettyJson.encodeToString(FlowPilotBackup.serializer(), backup)
    }

    fun exportSingleToString(rule: Automation): String {
        val plainRule = rule.withDecryptedSecrets()
        return prettyJson.encodeToString(Automation.serializer(), plainRule)
    }

    fun parseImport(jsonContent: String): Result<List<Automation>> {
        val trimmed = jsonContent.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty content"))
        }

        // 1. Try parsing as full FlowPilotBackup
        try {
            val backup = parserJson.decodeFromString(FlowPilotBackup.serializer(), trimmed)
            if (backup.automations.isNotEmpty()) {
                return Result.success(backup.automations)
            }
        } catch (_: Throwable) {}

        // 2. Try parsing as List<Automation>
        try {
            val list = parserJson.decodeFromString(ListSerializer(Automation.serializer()), trimmed)
            if (list.isNotEmpty()) {
                return Result.success(list)
            }
        } catch (_: Throwable) {}

        // 3. Try parsing as single Automation
        try {
            val single = parserJson.decodeFromString(Automation.serializer(), trimmed)
            return Result.success(listOf(single))
        } catch (_: Throwable) {}

        return Result.failure(IllegalArgumentException("Invalid JSON format for automations"))
    }

    fun prepareShareFile(context: Context, fileName: String, content: String): Uri {
        val shareDir = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(shareDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun generateBackupFileName(): String {
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "flowpilot_backup_$dateStr.json"
    }

    fun generateRuleFileName(ruleName: String): String {
        val safeName = ruleName.trim()
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(30)
            .ifBlank { "rule" }
        return "flowpilot_$safeName.json"
    }
}
