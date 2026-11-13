package com.flowpilot.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Surface
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowpilot.app.R
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.ui.components.UpdateDialog
import com.flowpilot.app.ui.screens.CreateScreen
import com.flowpilot.app.ui.screens.DetailScreen
import com.flowpilot.app.ui.screens.HistoryScreen
import com.flowpilot.app.ui.screens.HomeScreen
import com.flowpilot.app.ui.screens.PermissionsScreen
import com.flowpilot.app.ui.screens.SettingsScreen
import com.flowpilot.app.ui.tutorial.LocalTutorialAnchors
import com.flowpilot.app.ui.tutorial.TutorialAnchors
import com.flowpilot.app.ui.tutorial.TutorialOverlay
import com.flowpilot.app.ui.tutorial.tutorialAnchor
import com.flowpilot.app.update.UpdateDownloadResult
import com.flowpilot.app.update.UpdateDownloader

enum class Page { HOME, SETTINGS, CREATE, PERMISSIONS, HISTORY, DETAIL }

@Composable
fun FlowPilotRoot(vm: AppViewModel = viewModel()) {
    var page by remember { mutableStateOf(Page.HOME) }
    var permissionsReturnPage by remember { mutableStateOf(Page.HOME) }
    var selectedRule by remember { mutableStateOf<Automation?>(null) }
    var inspectedRule by remember { mutableStateOf<Automation?>(null) }
    var inspectReturnPage by remember { mutableStateOf(Page.HOME) }
    var initialPreset by remember { mutableStateOf<com.flowpilot.app.data.model.AutomationPreset?>(null) }
    val context = LocalContext.current
    val updateAvailable by vm.updateAvailable.collectAsState()
    val updateDialogVisible by vm.updateDialogVisible.collectAsState()
    val anchors = remember { TutorialAnchors() }
    val tutorialVisible by vm.tutorialVisible.collectAsState()

    LaunchedEffect(tutorialVisible) {
        if (tutorialVisible && page != Page.HOME) page = Page.HOME
    }

    BackHandler(enabled = page != Page.HOME) {
        if (page == Page.DETAIL && inspectedRule != null) {
            inspectedRule = null
            page = inspectReturnPage
        } else {
            page = Page.HOME
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        CompositionLocalProvider(LocalTutorialAnchors provides anchors) {
            Crossfade(
                targetState = if (inspectedRule != null) inspectReturnPage else page,
                animationSpec = tween(180),
                label = "pageCrossfade",
            ) { targetPage ->
                when (targetPage) {
                    Page.HOME -> HomeScreen(
                        vm = vm,
                        detail = { selectedRule = it; page = Page.DETAIL },
                        inspectRule = { rule ->
                            inspectedRule = rule
                            inspectReturnPage = Page.HOME
                            page = Page.DETAIL
                        },
                        showConflictWarning = inspectedRule == null,
                        create = { initialPreset = null; page = Page.CREATE },
                        createWithPreset = { preset ->
                            initialPreset = preset
                            page = Page.CREATE
                        },
                        settings = { page = Page.SETTINGS },
                        permissions = { permissionsReturnPage = Page.HOME; page = Page.PERMISSIONS },
                        bottomBar = { BottomBar(Page.HOME) { page = it } },
                    )
                    Page.SETTINGS -> SettingsScreen(
                        vm = vm,
                        permissions = {
                            permissionsReturnPage = Page.SETTINGS
                            page = Page.PERMISSIONS
                        },
                        history = { page = Page.HISTORY },
                        bottomBar = { BottomBar(Page.SETTINGS) { page = it } },
                    )
                    Page.CREATE -> CreateScreen(
                        vm = vm,
                        initialPreset = initialPreset,
                        inspectRule = { rule ->
                            inspectedRule = rule
                            inspectReturnPage = Page.CREATE
                            page = Page.DETAIL
                        },
                        showConflictWarning = inspectedRule == null,
                    ) {
                        initialPreset = null
                        page = Page.HOME
                    }
                    Page.PERMISSIONS -> PermissionsScreen(vm) { page = permissionsReturnPage }
                    Page.HISTORY -> HistoryScreen(
                        vm = vm,
                        back = { page = Page.SETTINGS },
                    )
                    Page.DETAIL -> {
                        selectedRule?.let { rule ->
                            DetailScreen(
                                vm = vm,
                                initialRule = rule,
                                inspectRule = {
                                    inspectedRule = it
                                    inspectReturnPage = Page.DETAIL
                                },
                                showConflictWarning = inspectedRule == null,
                            ) { page = Page.HOME }
                        } ?: run {
                            page = Page.HOME
                        }
                    }
                }
            }
            if (page == Page.DETAIL && inspectedRule != null) {
                DetailScreen(
                    vm = vm,
                    initialRule = inspectedRule!!,
                    inspectRule = { inspectedRule = it },
                ) {
                    inspectedRule = null
                    page = inspectReturnPage
                }
            }
        }
        TutorialOverlay(
            visible = tutorialVisible && page == Page.HOME,
            anchors = anchors,
            onFinish = vm::finishTutorial,
            onOpenPresets = vm::requestTutorialPresets,
        )
    }

    val update = updateAvailable
    if (updateDialogVisible && update != null) {
        UpdateDialog(
            update = update,
            onDismiss = vm::dismissUpdateDialog,
            onDownload = {
                when (val result = UpdateDownloader.start(context, update)) {
                    is UpdateDownloadResult.Started -> {
                        Toast.makeText(context, R.string.update_download_started, Toast.LENGTH_SHORT).show()
                        vm.dismissUpdateDialog()
                    }
                    UpdateDownloadResult.PermissionRequired -> {
                        Toast.makeText(
                            context,
                            R.string.update_install_permission_required,
                            Toast.LENGTH_LONG,
                        ).show()
                        UpdateDownloader.openInstallPermissionSettings(context)
                    }
                    is UpdateDownloadResult.Failed -> {
                        Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                    }
                    UpdateDownloadResult.NoAsset -> {
                        Toast.makeText(context, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                        UpdateDownloader.openReleasePage(context, update)
                        vm.dismissUpdateDialog()
                    }
                }
            },
            onOpenRelease = {
                UpdateDownloader.openReleasePage(context, update)
                vm.dismissUpdateDialog()
            },
            onRemindLater = vm::remindUpdateLater,
            onSkipVersion = vm::skipUpdateVersion,
        )
    }
}

@Composable
private fun BottomBar(current: Page, onSelect: (Page) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.navigationBars,
    ) {
        NavigationBarItem(
            modifier = Modifier.tutorialAnchor("home_bottom_home"),
            selected = current == Page.HOME,
            onClick = { onSelect(Page.HOME) },
            icon = { Icon(Icons.Default.Bolt, null) },
            label = { Text(androidx.compose.ui.res.stringResource(com.flowpilot.app.R.string.nav_home)) },
        )
        NavigationBarItem(
            modifier = Modifier.tutorialAnchor("home_bottom_settings"),
            selected = current == Page.SETTINGS,
            onClick = { onSelect(Page.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text(androidx.compose.ui.res.stringResource(com.flowpilot.app.R.string.nav_settings)) },
        )
    }
}
