package com.flowpilot.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flowpilot.app.data.model.ActionType
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.data.model.TriggerEvent
import com.flowpilot.app.permission.CapabilityStatus
import com.flowpilot.app.ui.AppViewModel
import com.flowpilot.app.ui.AutomationUI
import com.flowpilot.app.ui.FlowPilotRoot
import org.junit.Rule
import org.junit.Test

class FlowPilotRootConflictNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun editInspectBackReturnsToPendingWarningAndAllowsOverride() {
        val candidate = rule("candidate", "Candidate", ActionType.WIFI_ON)
        val other = rule("other", "Other", ActionType.WIFI_OFF)
        val vm = AppViewModel(compose.activity.application)
        vm.automations.value = listOf(
            AutomationUI(candidate, CapabilityStatus.AVAILABLE),
            AutomationUI(other, CapabilityStatus.AVAILABLE),
        )
        compose.setContent { FlowPilotRoot(vm) }

        compose.onNodeWithText("Candidate").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Likely automation conflict").assertIsDisplayed()
        compose.onNodeWithText("Inspect Other").performClick()
        compose.onNodeWithText("Other").assertIsDisplayed()

        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Likely automation conflict").assertIsDisplayed()
        compose.onNodeWithText("Save anyway").performClick()
        compose.onNodeWithText("Candidate").assertIsDisplayed()
    }

    private fun rule(id: String, name: String, action: ActionType) = Automation(
        id = id,
        name = name,
        enabled = true,
        triggerEvent = TriggerEvent.SCREEN_ON,
        action = action,
        actions = listOf(action),
        createdAt = 1L,
    )
}
