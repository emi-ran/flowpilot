package com.flowpilot.app

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
        var inspected = false
        compose.setContent {
            com.flowpilot.app.ui.components.ConflictWarningDialog(
                conflicts = listOf(
                    com.flowpilot.app.analysis.AutomationConflict(
                        ruleId = "opposing-state-actions",
                        conflictingRuleId = "other",
                        overlapReason = "Exact trigger target and conditions overlap",
                        candidateAction = com.flowpilot.app.data.model.ActionType.WIFI_ON,
                        conflictingAction = com.flowpilot.app.data.model.ActionType.WIFI_OFF,
                        confidence = com.flowpilot.app.analysis.ConflictConfidence.CERTAIN,
                    ),
                ),
                ruleNames = mapOf("other" to "Office Wi-Fi"),
                onInspect = { inspected = true },
                onOverride = { overridden = true },
                onDismiss = {},
            )
        }

        compose.onNodeWithText("Likely automation conflict").assertIsDisplayed()
        compose.onNodeWithText("Inspect Office Wi-Fi").performClick()
        compose.runOnIdle { assert(inspected); assert(!overridden) }
        compose.onNodeWithText("Save anyway").performClick()
        compose.runOnIdle { assert(overridden) }
    }
}
