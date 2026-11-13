package com.flowpilot.app.actions

import android.Manifest
import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.OperationApplicationException
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import com.flowpilot.app.data.model.ActionType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarExecutorTest {
    private val context = RuntimeEnvironment.getApplication()
    private val provider = CalendarProvider()
    private val selected = ActionParameters(calendarAccountName = "private account", calendarAccountType = "type", calendarName = "private calendar", calendarTitle = "private title", calendarDescription = "private description", calendarLocation = "private location", calendarStartMillis = 100L, calendarEndMillis = 200L)

    @Before
    fun setup() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
    }

    @Test
    fun insertsEventWithSelectedCalendarAndFields() {
        val result = CalendarExecutor(context).execute(ActionType.CREATE_CALENDAR_EVENT, selected)
        assertThat(provider.selectionArgs).isNotNull()
        assertThat(provider.inserted).isNotNull()
        assertThat(result.message).isEqualTo("Calendar event created")
        assertThat(result.message).doesNotContain("private")
        assertThat(provider.selectionArgs!!.toList()).containsExactly("private account", "type", "private calendar", CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()).inOrder()
        val values = provider.inserted!!
        assertThat(values.getAsLong(CalendarContract.Events.CALENDAR_ID)).isEqualTo(42L)
        assertThat(values.getAsString(CalendarContract.Events.TITLE)).isEqualTo("private title")
        assertThat(values.getAsString(CalendarContract.Events.DESCRIPTION)).isEqualTo("private description")
        assertThat(values.getAsString(CalendarContract.Events.EVENT_LOCATION)).isEqualTo("private location")
        assertThat(values.getAsLong(CalendarContract.Events.DTSTART)).isEqualTo(100L)
        assertThat(values.getAsLong(CalendarContract.Events.DTEND)).isEqualTo(200L)
        assertThat(values.getAsString(CalendarContract.Events.EVENT_TIMEZONE)).isEqualTo(TimeZone.getDefault().id)
        assertThat(provider.batchSize).isEqualTo(1)
        assertThat(values.containsKey(CalendarContract.Events.RRULE)).isFalse()
        assertThat(values.containsKey(CalendarContract.Events.DURATION)).isFalse()
        assertThat(provider.reminder).isNull()
    }

    @Test
    fun recurringEventsAndRemindersUseAtomicBatch() {
        for (repeat in listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) {
            val result = CalendarExecutor(context).execute(ActionType.CREATE_CALENDAR_EVENT, selected.copy(calendarRepeat = repeat, calendarReminderMinutes = 40_320))
            assertThat(result.success).isTrue()
            assertThat(provider.batchSize).isEqualTo(2)
            assertThat(provider.inserted!!.getAsString(CalendarContract.Events.RRULE)).isEqualTo("FREQ=$repeat")
            assertThat(provider.inserted!!.getAsString(CalendarContract.Events.DURATION)).isEqualTo("PT1S")
            assertThat(provider.inserted!!.containsKey(CalendarContract.Events.DTEND)).isFalse()
            assertThat(provider.reminder!!.getAsLong(CalendarContract.Reminders.EVENT_ID)).isEqualTo(1L)
            assertThat(provider.reminder!!.getAsInteger(CalendarContract.Reminders.MINUTES)).isEqualTo(40_320)
            assertThat(provider.reminder!!.getAsInteger(CalendarContract.Reminders.METHOD)).isEqualTo(CalendarContract.Reminders.METHOD_ALERT)
        }
    }

    @Test
    fun rejectsInvalidOptionsAndUnsupportedReminderCapabilities() {
        for (parameters in listOf(selected.copy(calendarRepeat = "INVALID"), selected.copy(calendarReminderMinutes = -2), selected.copy(calendarReminderMinutes = 40_321))) {
            assertThat(CalendarExecutor(context).execute(ActionType.CREATE_CALENDAR_EVENT, parameters).diagnostic?.reason).isEqualTo(ActionDiagnosticReason.INVALID_INPUT)
        }
        for ((max, methods) in listOf(0 to "1", 1 to "0")) {
            provider.maxReminders = max
            provider.allowedReminders = methods
            assertThat(CalendarExecutor(context).execute(ActionType.CREATE_CALENDAR_EVENT, selected.copy(calendarReminderMinutes = 0)).success).isFalse()
            assertThat(provider.inserted).isNull()
        }
    }

    @Test
    fun unknownReminderCapabilitiesAreAttempted() {
        for ((max, methods) in listOf(null to "1", 1 to null, null to null)) {
            provider.maxReminders = max
            provider.allowedReminders = methods
            assertThat(CalendarExecutor(context).execute(ActionType.CREATE_CALENDAR_EVENT, selected.copy(calendarReminderMinutes = 0)).success).isTrue()
            assertThat(provider.reminder!!.getAsInteger(CalendarContract.Reminders.MINUTES)).isEqualTo(0)
        }
    }

    @Test
    fun reminderBatchFailureDoesNotLeaveEvent() {
        provider.failReminder = true
        val result = CalendarExecutor(context).execute(ActionType.CREATE_CALENDAR_EVENT, selected.copy(calendarReminderMinutes = 0))
        assertThat(result.success).isFalse()
        assertThat(result.message).doesNotContain("private")
        assertThat(provider.inserted).isNull()
    }

    @Test
    fun recurringDefaultUsesExecutionTimeAndTimeZone() {
        val result = CalendarExecutor(context, clock = { 10_000L }).execute(ActionType.CREATE_CALENDAR_EVENT, selected.copy(calendarStartMillis = 0, calendarEndMillis = 0, calendarRepeat = "DAILY"))
        assertThat(result.success).isTrue()
        assertThat(provider.inserted!!.getAsLong(CalendarContract.Events.DTSTART)).isEqualTo(10_000L)
        assertThat(provider.inserted!!.getAsString(CalendarContract.Events.DURATION)).isEqualTo("PT3600S")
        assertThat(provider.inserted!!.getAsString(CalendarContract.Events.EVENT_TIMEZONE)).isEqualTo(TimeZone.getDefault().id)
    }

    @Test
    fun recurringDurationHandlesLargeRangesWithoutOverflow() {
        val result = CalendarExecutor(context).execute(ActionType.CREATE_CALENDAR_EVENT, selected.copy(calendarStartMillis = 1, calendarEndMillis = Long.MAX_VALUE, calendarRepeat = "YEARLY"))
        assertThat(result.success).isTrue()
        assertThat(provider.inserted!!.getAsString(CalendarContract.Events.DURATION)).isEqualTo("PT9223372036854776S")
    }

    @Test
    fun defaultTimesUseRuntimeStartAndOneHourDuration() {
        val result = CalendarExecutor(context, clock = { 10_000L }).execute(ActionType.CREATE_CALENDAR_EVENT, selected.copy(calendarStartMillis = 0, calendarEndMillis = 0))
        assertThat(result.message).isEqualTo("Calendar event created")
        assertThat(provider.inserted!!.getAsLong(CalendarContract.Events.DTSTART)).isEqualTo(10_000L)
        assertThat(provider.inserted!!.getAsLong(CalendarContract.Events.DTEND)).isEqualTo(3_610_000L)
    }

    @Test
    fun rejectsInvalidTimesAndMissingSelection() {
        for (parameters in listOf(selected.copy(calendarStartMillis = -1), selected.copy(calendarEndMillis = -1), selected.copy(calendarEndMillis = 100), selected.copy(calendarStartMillis = Long.MAX_VALUE, calendarEndMillis = 0), selected.copy(calendarStartMillis = 0, calendarEndMillis = 99), selected.copy(calendarName = ""))) {
            val result = CalendarExecutor(context, clock = { 100L }).execute(ActionType.CREATE_CALENDAR_EVENT, parameters)
            assertThat(result.success).isFalse()
            assertThat(result.diagnostic?.reason).isEqualTo(ActionDiagnosticReason.INVALID_INPUT)
        }
        assertThat(provider.inserted).isNull()
    }

    @Test
    fun requiresBothPermissions() {
        shadowOf(context).denyPermissions(Manifest.permission.WRITE_CALENDAR)
        val result = CalendarExecutor(context).execute(ActionType.CREATE_CALENDAR_EVENT, selected)
        assertThat(result.success).isFalse()
        assertThat(result.resultCode).isEqualTo(ActionResultCode.PERMISSION_REQUIRED)
        assertThat(provider.inserted).isNull()
    }

    @Test
    fun missingOrAmbiguousCalendarAndInsertFailureAreSafe() {
        for (count in listOf(0, 2)) {
            provider.count = count
            val result = CalendarExecutor(context).execute(ActionType.CREATE_CALENDAR_EVENT, selected)
            assertThat(result.success).isFalse()
            assertThat(result.message).doesNotContain("private")
            assertThat(provider.inserted).isNull()
        }
        provider.count = 1
        provider.failInsert = true
        val result = CalendarExecutor(context).execute(ActionType.CREATE_CALENDAR_EVENT, selected)
        assertThat(result.success).isFalse()
        assertThat(result.message).doesNotContain("private")
    }

    @Test
    fun editorRejectsInvalidRanges() {
        assertThat(CalendarExecutor.validEditorTimes(200, 100, 50)).isFalse()
        assertThat(CalendarExecutor.validEditorTimes(100, 100, 50)).isFalse()
        assertThat(CalendarExecutor.validEditorTimes(0, 100, 100)).isFalse()
        assertThat(CalendarExecutor.validEditorTimes(0, 101, 100)).isTrue()
        assertThat(CalendarExecutor.validEditorTimes(100, 0, 200)).isTrue()
    }

    private class CalendarProvider : ContentProvider() {
        var count = 1
        var failInsert = false
        var inserted: ContentValues? = null
        var reminder: ContentValues? = null
        var maxReminders: Int? = 5
        var allowedReminders: String? = "0,1"
        var failReminder = false
        var batchSize = 0
        var selectionArgs: Array<out String>? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            this.selectionArgs = selectionArgs
            return MatrixCursor(arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.MAX_REMINDERS, CalendarContract.Calendars.ALLOWED_REMINDERS)).apply { repeat(this@CalendarProvider.count) { addRow(arrayOf(42L + it, maxReminders, allowedReminders)) } }
        }
        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            if (failInsert) throw IllegalStateException("private provider failure")
            if (uri == CalendarContract.Reminders.CONTENT_URI) reminder = values else inserted = values
            return Uri.withAppendedPath(uri, "1")
        }
        override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> {
            batchSize = operations.size
            if (failReminder && operations.size > 1) throw OperationApplicationException("private provider failure")
            return super.applyBatch(operations)
        }
        override fun getType(uri: Uri): String? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
