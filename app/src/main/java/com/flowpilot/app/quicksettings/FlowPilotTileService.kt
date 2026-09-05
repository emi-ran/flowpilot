package com.flowpilot.app.quicksettings

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.flowpilot.app.R
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.engine.AutomationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FlowPilotTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listening: Job? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        AutomationService.loadFailure(this)
        val repository = AutomationRepository(applicationContext)
        listening = serviceScope.launch {
            combine(repository.isEngineEnabled, repository.automations,
                AutomationService.running, AutomationService.failure) { enabled, rules, running, failed ->
                val tile = qsTile ?: return@combine
                tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_engine_name)
                tile.icon = Icon.createWithResource(applicationContext,
                    if (running) R.drawable.ic_widget_bolt else R.drawable.ic_widget_pause)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = when {
                        failed -> getString(R.string.notif_engine_failure_title)
                        running -> getString(R.string.tile_status_active, rules.count { it.enabled })
                        enabled -> getString(R.string.engine_not_running)
                        else -> getString(R.string.tile_status_paused)
                    }
                }
                tile.updateTile()
            }.collect {}
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch { AutomationService.toggleEnabled(applicationContext) }
    }
}
