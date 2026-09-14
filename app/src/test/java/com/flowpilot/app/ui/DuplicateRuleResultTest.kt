package com.flowpilot.app.ui

import com.flowpilot.app.data.model.Automation
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DuplicateRuleResultTest {

    @Test
    fun duplicateRuleResult_missingSource_returnsFailure() = runTest {
        val result = duplicateRuleResult { null }

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun duplicateRuleResult_repositoryError_returnsFailure() = runTest {
        val result = duplicateRuleResult { error("write failed") }

        assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("write failed")
    }

    @Test
    fun duplicateRuleResult_createdClone_returnsSuccess() = runTest {
        val clone = Automation(id = "clone-id", name = "Copy")

        assertThat(duplicateRuleResult { clone }.getOrNull()).isEqualTo(clone)
    }
}
