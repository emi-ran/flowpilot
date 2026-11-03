package com.flowpilot.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import com.flowpilot.app.data.model.TriggerEvent

/**
 * Text field for a template-capable action value. The button below the field opens the shared
 * trigger-aware [VariablePickerDialog] and inserts the chosen token at the cursor.
 *
 * The picker is used by every action field that [com.flowpilot.app.actions.ActionParametersFactory]
 * renders, so variables are offered consistently instead of through per-action shortcuts.
 */
@Composable
fun TemplateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    triggers: Collection<TriggerEvent>,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var showVariablePicker by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    fun insertToken(token: String) {
        fieldValue = insertVariableAtSelection(fieldValue, token)
        onValueChange(fieldValue.text)
    }

    if (showVariablePicker) {
        VariablePickerDialog(
            triggers = triggers,
            onInsert = ::insertToken,
            onDismiss = { showVariablePicker = false },
        )
    }

    Column(modifier) {
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onValueChange(it.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewOnFocusOrChange(fieldValue.text),
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            shape = shape,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            isError = isError,
            supportingText = supportingText,
        )
        TextButton(
            onClick = { showVariablePicker = true },
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.variable_picker_insert),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

internal fun insertVariableAtSelection(
    fieldValue: TextFieldValue,
    token: String,
): TextFieldValue {
    val start = fieldValue.selection.min.coerceIn(0, fieldValue.text.length)
    val end = fieldValue.selection.max.coerceIn(start, fieldValue.text.length)
    val updated = fieldValue.text.replaceRange(start, end, token)
    return TextFieldValue(updated, selection = TextRange(start + token.length))
}
