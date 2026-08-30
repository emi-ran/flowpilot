package com.flowpilot.app

import android.app.Application
import com.flowpilot.app.actions.ShizukuShell

class FlowPilotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuShell.instance.init(this)
    }
}
