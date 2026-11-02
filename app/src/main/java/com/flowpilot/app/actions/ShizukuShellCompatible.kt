package com.flowpilot.app.actions

/** Bounds the Shizuku surface used by executors, so tests can provide a fake. */
interface ShizukuShellCompatible {
    fun isShizukuRunning(): Boolean
    fun hasPermission(): Boolean
    /** Run a shell command with privileges; returns (exitCode, output). */
    fun run(command: String): Pair<Int, String>

    /**
     * Runs the shell-side [HotspotBridge] as the shell UID; returns (exitCode, output).
     * Default keeps simple fakes compiling; real implementations must override it.
     */
    fun runHotspot(enabled: Boolean): Pair<Int, String> = -1 to "Hotspot control is unavailable"
}
