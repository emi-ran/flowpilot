package com.flowpilot.app

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flowpilot.app.data.model.ActionType
import org.junit.Rule
import org.junit.Test

class ConflictWarningDialogTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun warningRequiresDeliberateOverrideAndOffersRuleInspection() {
        var overridden = false
        compose.setContent {
            var inspectedRuleId by remember { mutableStateOf<String?>(null) }
            if (inspectedRuleId == null) {
                com.flowpilot.app.ui.components.ConflictWarningDialog(
                    conflicts = listOf(
                        com.flowpilot.app.analysis.AutomationConflict(
                            ruleId = "opposing-state-actions",
                            candidateRuleId = "candidate",
                            candidateRuleName = "Home Wi-Fi",
                            conflictingRuleId = "other",
                            conflictingRuleName = "Office Wi-Fi",
                            overlapReason = "Exact trigger target and conditions overlap",
                            candidateAction = com.flowpilot.app.data.model.ActionType.WIFI_ON,
                            conflictingAction = com.flowpilot.app.data.model.ActionType.WIFI_OFF,
                            confidence = com.flowpilot.app.analysis.ConflictConfidence.CERTAIN,
                        ),
                    ),
                    onInspect = { inspectedRuleId = it },
                    onOverride = { overridden = true },
                    onDismiss = {},
                )
            } else {
                Text("Rule detail: $inspectedRuleId")
            }
        }

        compose.onNodeWithText("Likely automation conflict").assertIsDisplayed()
        compose.onNodeWithText("Inspect Office Wi-Fi").performClick()
        compose.onNodeWithText("Rule detail: other").assertIsDisplayed()
        compose.runOnIdle { assert(!overridden) }
    }

    @Test
    fun oneRuleShowsEveryUniqueOpposingPairAndOneInspectAction() {
        val wifi = conflict(ActionType.WIFI_ON, ActionType.WIFI_OFF)
        val bluetooth = conflict(ActionType.BLUETOOTH_ON, ActionType.BLUETOOTH_OFF)
        compose.setContent {
            com.flowpilot.app.ui.components.ConflictWarningDialog(
                conflicts = listOf(wifi, bluetooth, wifi),
                onInspect = {},
                onOverride = {},
                onDismiss = {},
            )
        }

        compose.onNodeWithText("Likely conflict: Turn Wi-Fi on versus Turn Wi-Fi off").assertIsDisplayed()
        compose.onNodeWithText("Likely conflict: Turn Bluetooth on versus Turn Bluetooth off").assertIsDisplayed()
        compose.onAllNodesWithText("Inspect Office Wi-Fi").assertCountEquals(1)
    }

    @Test
    fun saveAnywayInvokesOverride() {
        var overridden = false
        compose.setContent {
            com.flowpilot.app.ui.components.ConflictWarningDialog(
                conflicts = listOf(
                    com.flowpilot.app.analysis.AutomationConflict(
                        ruleId = "opposing-state-actions",
                        candidateRuleId = "candidate",
                        candidateRuleName = "Home Wi-Fi",
                        conflictingRuleId = "other",
                        conflictingRuleName = "Office Wi-Fi",
                        overlapReason = "Exact trigger target and conditions overlap",
                        candidateAction = com.flowpilot.app.data.model.ActionType.WIFI_ON,
                        conflictingAction = com.flowpilot.app.data.model.ActionType.WIFI_OFF,
                        confidence = com.flowpilot.app.analysis.ConflictConfidence.CERTAIN,
                    ),
                ),
                onInspect = {},
                onOverride = { overridden = true },
                onDismiss = {},
            )
        }

        compose.onNodeWithText("Save anyway").performClick()
        compose.runOnIdle { assert(overridden) }
    }

    private fun conflict(candidate: ActionType, other: ActionType) =
        com.flowpilot.app.analysis.AutomationConflict(
            ruleId = "opposing-state-actions",
            candidateRuleId = "candidate",
            candidateRuleName = "Home Wi-Fi",
            conflictingRuleId = "other",
            conflictingRuleName = "Office Wi-Fi",
            overlapReason = "Exact trigger target and conditions overlap",
            candidateAction = candidate,
            conflictingAction = other,
            confidence = com.flowpilot.app.analysis.ConflictConfidence.CERTAIN,
        )
}
