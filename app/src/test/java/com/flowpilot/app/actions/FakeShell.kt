package com.flowpilot.app.actions

class FakeShell(
    private val running: Boolean = true,
    private val permitted: Boolean = true,
    private val results: Map<String, Pair<Int, String>> = emptyMap(),
) : ShizukuShellCompatible {
    val commands = mutableListOf<String>()

    override fun isShizukuRunning(): Boolean = running

    override fun hasPermission(): Boolean = permitted

    override fun run(command: String): Pair<Int, String> {
        commands += command
        return results[command] ?: (-1 to "Unexpected command: $command")
    }
}
