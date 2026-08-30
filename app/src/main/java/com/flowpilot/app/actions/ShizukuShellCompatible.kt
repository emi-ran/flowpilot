package com.flowpilot.app.actions

/** Bounds the Shizuku surface used by executors, so tests can provide a fake. */
interface ShizukuShellCompatible {
    fun isShizukuRunning(): Boolean
    fun hasPermission(): Boolean
    /** Run a shell command with privileges; returns (exitCode, output). */
    fun run(command: String): Pair<Int, String>
}
