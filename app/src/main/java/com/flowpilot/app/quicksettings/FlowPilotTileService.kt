package com.flowpilot.app.quicksettings

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.flowpilot.app.R
import com.flowpilot.app.data.AutomationRepository
import com.flowpilot.app.engine.AutomationService
import com.flowpilot.app.widget.FlowPilotWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FlowPilotTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var repository: AutomationRepository

    override fun onCreate() {
        super.onCreate()
        repository = AutomationRepository(applicationContext)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val isEnabled = repository.isEngineEnabled.first()
            val newEnabled = !isEnabled
            repository.setEngineEnabled(newEnabled)

            if (newEnabled) {
                AutomationService.start(applicationContext)
            } else {
                AutomationService.stop(applicationContext)
            }

            updateTileState()
            FlowPilotWidgetProvider.updateAllWidgets(applicationContext)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        serviceScope.launch {
            try {
                val isEnabled = repository.isEngineEnabled.first()
                val rules = repository.automations.first()
                val activeCount = rules.count { it.enabled }

                tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_engine_name)
                tile.icon = Icon.createWithResource(
                    applicationContext,
                    if (isEnabled) R.drawable.ic_widget_bolt else R.drawable.ic_widget_pause
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = if (isEnabled) {
                        getString(R.string.tile_status_active, activeCount)
                    } else {
                        getString(R.string.tile_status_paused)
                    }
                }
                tile.updateTile()
            } catch (_: Throwable) {}
        }
    }
}
