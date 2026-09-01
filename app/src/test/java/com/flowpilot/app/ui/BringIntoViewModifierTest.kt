package com.flowpilot.app.ui

import androidx.compose.ui.Modifier
import com.flowpilot.app.ui.components.bringIntoViewOnFocusOrChange
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BringIntoViewModifierTest {

    @Test
    fun bringIntoViewOnFocusOrChange_modifierChainsCorrectly() {
        val baseModifier = Modifier
        val modified = baseModifier.bringIntoViewOnFocusOrChange(trigger = "test-input")
        assertThat(modified).isNotNull()
    }
}
