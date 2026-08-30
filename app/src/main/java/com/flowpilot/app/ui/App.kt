@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.flowpilot.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowpilot.app.data.model.Automation
import com.flowpilot.app.ui.screens.*

enum class Page {
    HOME, SETTINGS, CREATE, PERMISSIONS, DETAIL
}

@Composable
fun FlowPilotRoot(vm: AppViewModel = viewModel()) {
    var page by remember { mutableStateOf(Page.HOME) }
    var selectedRule by remember { mutableStateOf<Automation?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (page == Page.HOME || page == Page.SETTINGS) {
                BottomBar(page) { page = it }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Crossfade(
                targetState = page,
                animationSpec = tween(150),
                label = "pageCrossfade",
            ) { targetPage ->
                when (targetPage) {
                    Page.HOME -> HomeScreen(
                        vm = vm,
                        detail = { selectedRule = it; page = Page.DETAIL },
                        create = { page = Page.CREATE },
                        settings = { page = Page.SETTINGS },
                        permissions = { page = Page.PERMISSIONS },
                    )
                    Page.SETTINGS -> SettingsScreen(vm) { page = Page.PERMISSIONS }
                    Page.CREATE -> CreateScreen(vm) { page = Page.HOME }
                    Page.PERMISSIONS -> PermissionsScreen(vm) { page = Page.HOME }
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