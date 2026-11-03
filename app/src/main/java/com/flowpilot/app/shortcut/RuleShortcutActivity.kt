package com.flowpilot.app.shortcut

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowpilot.app.MainActivity
import com.flowpilot.app.R
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.engine.ManualRuleRunner
import com.flowpilot.app.ui.theme.FlowPilotTheme
import com.flowpilot.app.ui.util.AppLocaleProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Confirmation gate for a pinned rule shortcut. It always reloads the rule and asks before
 * executing, so a stale shortcut cannot silently run a deleted, disabled, or changed rule.
 */
class RuleShortcutActivity : ComponentActivity() {

    private val repository by lazy { AutomationRepository(applicationContext) }
    private val runner by lazy { ManualRuleRunner(applicationContext, repository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ruleId = intent?.getStringExtra(RuleShortcutManager.EXTRA_RULE_ID)?.takeIf { it.isNotBlank() }
        if (ruleId == null) {
            finish()
            return
        }
        setContent {
            val language by repository.appLanguage.collectAsStateWithLifecycle(initialValue = "system")
            val appTheme by repository.appTheme.collectAsStateWithLifecycle(initialValue = "system")
            val darkTheme = when (appTheme.lowercase()) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            AppLocaleProvider(language) {
                FlowPilotTheme(darkTheme = darkTheme) {
                    RuleShortcutScreen(
                        ruleId = ruleId,
                        repository = repository,
                        runner = runner,
                        language = language,
                        onFinish = ::finish,
                        onOpenApp = {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        },
                    )
                }
            }
        }
    }
}

private sealed interface ShortcutState {
    data object Loading : ShortcutState
    data object Missing : ShortcutState
    data class Ready(val rule: Automation) : ShortcutState
    data class Disabled(val rule: Automation) : ShortcutState
}

@Composable
private fun RuleShortcutScreen(
    ruleId: String,
    repository: AutomationRepository,
    runner: ManualRuleRunner,
    language: String,
    onFinish: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ShortcutState>(ShortcutState.Loading) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(ruleId) {
        val rule = repository.automations.first().firstOrNull { it.id == ruleId }
        state = when {
            rule == null -> ShortcutState.Missing
            !rule.enabled -> ShortcutState.Disabled(rule)
            else -> ShortcutState.Ready(rule)
        }
    }

    when (val current = state) {
        ShortcutState.Loading -> Unit
        ShortcutState.Missing -> LaunchedEffect(Unit) {
            Toast.makeText(context, context.getString(R.string.shortcut_missing), Toast.LENGTH_SHORT).show()
            onFinish()
        }
        is ShortcutState.Disabled -> AlertDialog(
            onDismissRequest = onFinish,
            title = { Text(current.rule.normalizedName) },
            text = { Text(stringResource(R.string.shortcut_disabled_desc)) },
            confirmButton = {
                TextButton(onClick = onOpenApp) {
                    Text(stringResource(R.string.widget_btn_open))
                }
            },
            dismissButton = {
                TextButton(onClick = onFinish) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
        is ShortcutState.Ready -> if (running) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(current.rule.normalizedName) },
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.shortcut_running))
                    }
                },
                confirmButton = {},
            )
        } else {
            AlertDialog(
                onDismissRequest = onFinish,
                title = { Text(stringResource(R.string.shortcut_confirm_title, current.rule.normalizedName)) },
                text = { Text(stringResource(R.string.shortcut_confirm_desc)) },
                confirmButton = {
                    TextButton(onClick = {
                        running = true
                        scope.launch {
                            val result = runner.run(current.rule, language)
                            val message = if (result.failureCount == 0) {
                                context.getString(R.string.test_run_executed_success, result.successCount)
                            } else {
                                context.getString(
                                    R.string.test_run_executed_errors,
                                    result.successCount,
                                    result.failureCount,
                                    result.failureMessages.joinToString(", "),
                                )
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            onFinish()
                        }
                    }) {
                        Text(stringResource(R.string.btn_run_now))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onFinish) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                },
            )
        }
    }
}
