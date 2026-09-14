package com.flowpilot.app

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
