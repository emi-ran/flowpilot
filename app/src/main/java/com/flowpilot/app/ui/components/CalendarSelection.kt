package com.flowpilot.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flowpilot.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarChoice(val accountName: String, val accountType: String, val name: String, val displayName: String)

class CalendarSelectionState {
    var hasPermissions by mutableStateOf(false)
    var calendars by mutableStateOf<List<CalendarChoice>>(emptyList())
    var loading by mutableStateOf(false)
    var queryFailed by mutableStateOf(false)
    var refresh by mutableIntStateOf(0)
    fun valid(account: String, type: String, name: String): Boolean =
        hasPermissions && !loading && !queryFailed && account.isNotBlank() && type.isNotBlank() && name.isNotBlank() &&
            calendars.count { it.accountName == account && it.accountType == type && it.name == name } == 1
}

private fun calendarPermissionsGranted(context: Context) = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR).all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/** Account label shown only when it adds information beyond the calendar name; the technical account type appears only to separate otherwise identical rows. */
private fun calendarSecondaryLabel(choice: CalendarChoice, calendars: List<CalendarChoice>): String? {
    val sharedIdentity = calendars.count {
        it.accountName == choice.accountName && it.displayName.equals(choice.displayName, ignoreCase = true)
    } > 1
    val parts = buildList {
        if (choice.accountName.isNotBlank() && !choice.accountName.equals(choice.displayName, ignoreCase = true)) add(choice.accountName)
        if (sharedIdentity && choice.accountType.isNotBlank()) add(choice.accountType)
    }
    return parts.joinToString(" · ").ifBlank { null }
}

@Composable
fun rememberCalendarSelectionState(): CalendarSelectionState {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val state = remember { CalendarSelectionState() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.refresh) {
        state.hasPermissions = calendarPermissionsGranted(context)
        state.calendars = emptyList()
        state.queryFailed = false
        if (state.hasPermissions) {
            state.loading = true
            val result: List<CalendarChoice>? = withContext(Dispatchers.IO) {
                try {
                    val columns = arrayOf(CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.NAME, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, columns,
                        "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?", arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)?.use { cursor ->
                        buildList<CalendarChoice> {
                            while (cursor.moveToNext()) {
                                val account = cursor.getString(0).orEmpty()
                                val type = cursor.getString(1).orEmpty()
                                val name = cursor.getString(2).orEmpty()
                                if (account.isNotBlank() && type.isNotBlank() && name.isNotBlank()) add(CalendarChoice(account, type, name, cursor.getString(3).orEmpty().ifBlank { name }))
                            }
                        }
                    }
                } catch (_: Exception) { null }
            }
            state.hasPermissions = calendarPermissionsGranted(context)
            state.queryFailed = result == null
            state.calendars = result.orEmpty()
            state.loading = false
        } else state.loading = false
    }
    return state
}

@Composable
fun CalendarSelectionEditor(state: CalendarSelectionState, account: String, type: String, name: String, onSelect: (CalendarChoice) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { state.refresh++ }
    val selected = state.calendars.singleOrNull { it.accountName == account && it.accountType == type && it.name == name }
    val support = when {
        !state.hasPermissions -> null
        state.queryFailed -> R.string.calendar_query_error
        state.calendars.isEmpty() -> R.string.calendar_empty
        selected == null && (account.isNotBlank() || type.isNotBlank() || name.isNotBlank()) -> R.string.calendar_selection_missing
        selected == null -> R.string.calendar_select_prompt
        else -> null
    }
    val supportIsHint = support == R.string.calendar_select_prompt

    Column(Modifier.fillMaxWidth().animateContentSize()) {
        if (!state.hasPermissions) {
            OutlinedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Event, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.calendar_permission_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SecondaryActionButton(
                        onClick = { launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Text(stringResource(R.string.calendar_grant_permissions), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
            OutlinedCard(
                onClick = { showPicker = true },
                enabled = !state.loading && !state.queryFailed && state.calendars.isNotEmpty(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        tint = if (selected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            selected?.displayName ?: stringResource(R.string.calendar_choose),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val secondary = selected?.let { calendarSecondaryLabel(it, state.calendars) }
                        if (state.loading && selected == null) {
                            Text(
                                stringResource(R.string.calendar_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (secondary != null) {
                            Text(
                                secondary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            AnimatedContent(targetState = support, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "calendarSupport") { status ->
                if (status != null) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            if (status == R.string.calendar_select_prompt) Icons.Default.Info else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (status == R.string.calendar_select_prompt) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 1.dp).size(14.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(status),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status == R.string.calendar_select_prompt) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        if (!supportIsHint) {
                            IconButton(onClick = { state.refresh++ }, enabled = !state.loading, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.calendar_retry), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.calendar_choose)) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup(),
                ) {
                    state.calendars.forEachIndexed { index, choice ->
                        val unique = state.calendars.count { it.accountName == choice.accountName && it.accountType == choice.accountType && it.name == choice.name } == 1
                        val isSelected = choice == selected
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .selectable(
                                    selected = isSelected,
                                    enabled = unique,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(choice); showPicker = false },
                                )
                                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        ) {
                            RadioButton(selected = isSelected, onClick = null, enabled = unique)
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    choice.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (unique) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val secondary = calendarSecondaryLabel(choice, state.calendars)
                                if (secondary != null) {
                                    Text(
                                        secondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!unique) {
                                    Text(
                                        stringResource(R.string.calendar_duplicate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.btn_cancel)) } },
        )
    }
}
