package com.flowpilot.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flowpilot.app.R
import com.flowpilot.app.analysis.AutomationConflict
import com.flowpilot.app.analysis.ConflictConfidence
import com.flowpilot.app.ui.util.localizedLabel

@Composable
fun ConflictWarningDialog(
    conflicts: List<AutomationConflict>,
    ruleNames: Map<String, String>,
    onInspect: (String) -> Unit,
    onOverride: () -> Unit,
    onDismiss: () -> Unit,
    overrideLabel: Int = R.string.conflict_save_anyway,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conflict_warning_title)) },
        text = {
            Column {
                Text(stringResource(R.string.conflict_warning_description))
                Spacer(Modifier.height(12.dp))
                conflicts.distinctBy { it.conflictingRuleId }.forEach { conflict ->
                    val confidence = stringResource(
                        if (conflict.confidence == ConflictConfidence.CERTAIN) R.string.conflict_likely
                        else R.string.conflict_possible,
                    )
                    Text(
                        stringResource(
                            R.string.conflict_warning_item,
                            confidence,
                            conflict.candidateAction.localizedLabel(),
                            conflict.conflictingAction.localizedLabel(),
                        ),
                    )
                    TextButton(onClick = { onInspect(conflict.conflictingRuleId) }) {
                        Text(stringResource(R.string.conflict_inspect_rule, ruleNames[conflict.conflictingRuleId].orEmpty()))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOverride) { Text(stringResource(overrideLabel)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}
