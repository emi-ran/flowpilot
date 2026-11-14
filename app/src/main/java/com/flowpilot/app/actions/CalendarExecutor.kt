package com.flowpilot.app.actions

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.flowpilot.app.data.model.ActionType
import java.util.TimeZone

/** Writes only to the explicitly selected writable calendar. */
class CalendarExecutor(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : ActionExecutor {
    override val supportedTypes: Set<ActionType> = setOf(ActionType.CREATE_CALENDAR_EVENT)

    override fun execute(action: ActionType, parameters: ActionParameters): ActionResult {
        if (action != ActionType.CREATE_CALENDAR_EVENT) return ActionResult(false, "Unsupported action for Calendar")
        val configuredStart = parameters.calendarStartMillis
        val configuredEnd = parameters.calendarEndMillis
        if (!validConfiguredTimes(configuredStart, configuredEnd)) return invalidTime()
        if (!validOptions(parameters.calendarRepeat, parameters.calendarReminderMinutes)) {
            return ActionResult(false, "Invalid calendar options", diagnostic = ActionDiagnostic(ActionDiagnosticReason.INVALID_INPUT))
        }
        val start = if (configuredStart == 0L) clock() else configuredStart
        if (start <= 0L || (configuredEnd == 0L && start > Long.MAX_VALUE - HOUR_MILLIS)) return invalidTime()
        val end = if (configuredEnd == 0L) start + HOUR_MILLIS else configuredEnd
        if (end <= start) return invalidTime()

        if (listOf(parameters.calendarAccountName, parameters.calendarAccountType, parameters.calendarName).any { it.isBlank() }) {
            return ActionResult(false, "Calendar selection required", diagnostic = ActionDiagnostic(ActionDiagnosticReason.INVALID_INPUT))
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return ActionResult(false, "Calendar permission required", resultCode = ActionResultCode.PERMISSION_REQUIRED)
        }
        return try {
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.MAX_REMINDERS, CalendarContract.Calendars.ALLOWED_REMINDERS),
                "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ? AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
                arrayOf(parameters.calendarAccountName, parameters.calendarAccountType, parameters.calendarName, CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                null,
            ) ?: return unavailable()
            val calendarId = cursor.use {
                if (!it.moveToFirst()) return unavailable()
                val id = it.getLong(0)
                if (parameters.calendarReminderMinutes >= 0) {
                    if ((!it.isNull(1) && it.getInt(1) < 1) ||
                        (!it.isNull(2) && it.getString(2).split(',').none { method -> method.trim() == CalendarContract.Reminders.METHOD_ALERT.toString() })) return unavailable()
                }
                if (it.moveToNext()) return unavailable()
                id
            }
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, parameters.calendarTitle)
                put(CalendarContract.Events.DESCRIPTION, parameters.calendarDescription)
                put(CalendarContract.Events.EVENT_LOCATION, parameters.calendarLocation)
                put(CalendarContract.Events.DTSTART, start)
                if (parameters.calendarRepeat == "NONE") {
                    put(CalendarContract.Events.DTEND, end)
                } else {
                    val durationMillis = end - start
                    val durationSeconds = durationMillis / 1_000L + if (durationMillis % 1_000L == 0L) 0L else 1L
                    put(CalendarContract.Events.DURATION, "PT${durationSeconds}S")
                    put(CalendarContract.Events.RRULE, "FREQ=${parameters.calendarRepeat}")
                }
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            }
            val operations = arrayListOf(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI).withValues(values).build())
            if (parameters.calendarReminderMinutes >= 0) {
                operations.add(
                    ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                        .withValueBackReference(CalendarContract.Reminders.EVENT_ID, 0)
                        .withValue(CalendarContract.Reminders.MINUTES, parameters.calendarReminderMinutes)
                        .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        .build(),
                )
            }
            val results = context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
            if (results.size != operations.size || results.any { it.uri == null }) unavailable()
            else ActionResult(true, "Calendar event created", resultCode = ActionResultCode.COMPLETED)
        } catch (_: SecurityException) {
            ActionResult(false, "Calendar permission required", resultCode = ActionResultCode.PERMISSION_REQUIRED)
        } catch (_: Exception) {
            unavailable()
        }
    }

    private fun invalidTime() = ActionResult(false, "Invalid calendar time", diagnostic = ActionDiagnostic(ActionDiagnosticReason.INVALID_INPUT))
    private fun unavailable() = ActionResult(false, "Calendar unavailable or selection is ambiguous", diagnostic = ActionDiagnostic(ActionDiagnosticReason.STATE_UNAVAILABLE))

    companion object {
        private const val HOUR_MILLIS = 3_600_000L

        fun validOptions(repeat: String, reminderMinutes: Int): Boolean =
            repeat in setOf("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY") &&
                (reminderMinutes == -1 || reminderMinutes in 0..40_320)

        /** Zero selects a runtime default; two explicit values must form a positive interval. */
        fun validConfiguredTimes(start: Long, end: Long): Boolean =
            start >= 0L && end >= 0L &&
                (start == 0L || end == 0L || end > start) &&
                (start == 0L || end != 0L || start <= Long.MAX_VALUE - HOUR_MILLIS)

        /** Editor-time check; dynamic start is checked again when the action executes. */
        fun validEditorTimes(start: Long, end: Long, now: Long = System.currentTimeMillis()): Boolean =
            validConfiguredTimes(start, end) && (start != 0L || end == 0L || end > now)
    }
}
