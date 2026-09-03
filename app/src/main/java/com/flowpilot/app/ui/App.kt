package com.flowpilot.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.ui.screens.CreateScreen
import com.flowpilot.app.ui.screens.DetailScreen
import com.flowpilot.app.ui.screens.HistoryScreen
import com.flowpilot.app.ui.screens.HomeScreen
import com.flowpilot.app.ui.screens.PermissionsScreen
import com.flowpilot.app.ui.screens.SettingsScreen

enum class Page { HOME, SETTINGS, CREATE, PERMISSIONS, HISTORY, DETAIL }

@Composable
fun FlowPilotRoot(vm: AppViewModel = viewModel()) {
    var page by remember { mutableStateOf(Page.HOME) }
    var permissionsReturnPage by remember { mutableStateOf(Page.HOME) }
    var selectedRule by remember { mutableStateOf<Automation?>(null) }

    BackHandler(enabled = page != Page.HOME) {
        page = Page.HOME
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(
            targetState = page,
            animationSpec = tween(180),
            label = "pageCrossfade",
        ) { targetPage ->
            when (targetPage) {
                Page.HOME -> HomeScreen(
                    vm = vm,
                    detail = { selectedRule = it; page = Page.DETAIL },
                    create = { page = Page.CREATE },
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
                Page.CREATE -> CreateScreen(vm) { page = Page.HOME }
                Page.PERMISSIONS -> PermissionsScreen(vm) { page = permissionsReturnPage }
                Page.HISTORY -> HistoryScreen(vm) { page = Page.SETTINGS }
                Page.DETAIL -> {
                    selectedRule?.let { rule ->
                        DetailScreen(vm, rule) { page = Page.HOME }
                    } ?: run {
                        page = Page.HOME
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(current: Page, onSelect: (Page) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        windowInsets = WindowInsets.navigationBars,
    ) {
        NavigationBarItem(
            selected = current == Page.HOME,
            onClick = { onSelect(Page.HOME) },
            icon = { Icon(Icons.Default.Bolt, null) },
            label = { Text("Automations") },
        )
        NavigationBarItem(
            selected = current == Page.SETTINGS,
            onClick = { onSelect(Page.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, null) },
            label = { Text("Settings") },
        )
    }
}
