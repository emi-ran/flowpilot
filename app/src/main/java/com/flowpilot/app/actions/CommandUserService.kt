package com.flowpilot.app.actions

import android.content.Context
import com.flowpilot.app.BuildConfig
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Keep
class CommandUserService : ICommandService.Stub {
    constructor() : super()
    constructor(context: Context) : super()

    override fun run(command: String): String {
        if (!isCommandAllowed(command)) {
            return "126\ncommand not allowed"
        }
        var process: Process? = null
        return try {
            // Allowlisted commands contain no shell metacharacters; `exec` replaces sh with the
            // command so destroying the process also ends the real command, not only the wrapper.
            val started = Runtime.getRuntime().exec(arrayOf("sh", "-c", "exec $command"))
            process = started
            val output = StringBuilder()
            val inThread = Thread {
                try {
                    BufferedReader(InputStreamReader(started.inputStream)).use { r ->
                        r.forEachLine { synchronized(output) { output.appendLine(it) } }
                    }
                } catch (_: Throwable) {}
            }.apply { isDaemon = true }
            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(started.errorStream)).use { r ->
                        r.forEachLine { synchronized(output) { output.appendLine(it) } }
                    }
                } catch (_: Throwable) {}
            }.apply { isDaemon = true }
            inThread.start()
            errThread.start()

            val finished = started.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                started.destroyForcibly()
            }
            inThread.join(1000)
            errThread.join(1000)

            if (!finished) {
                return "124\ncommand timed out"
            }
            val text = synchronized(output) { output.toString().trim() }
            "${started.exitValue()}\n$text"
        } catch (t: Throwable) {
            if (t is InterruptedException) Thread.currentThread().interrupt()
            "125\n${t.message ?: t.javaClass.simpleName}"
        } finally {
            process?.let { p ->
                try { p.destroyForcibly() } catch (_: Throwable) {}
                try { p.inputStream.close() } catch (_: Throwable) {}
                try { p.errorStream.close() } catch (_: Throwable) {}
                try { p.outputStream.close() } catch (_: Throwable) {}
            }
        }
    }

    companion object {
        fun isCommandAllowed(command: String): Boolean {
            if (command in allowedCommands) return true
            if (command.startsWith("am force-stop ")) {
                val pkg = command.removePrefix("am force-stop ").trim()
                return pkg.matches(Regex("""^[a-zA-Z0-9_]+(\.[a-zA-Z0-9_]+)+$"""))
            }
            return false
        }

        val allowedCommands = setOf(
            "svc nfc enable",
            "svc nfc disable",
            "svc bluetooth enable",
            "svc bluetooth disable",
            "svc data enable",
            "svc data disable",
            "svc wifi enable",
            "svc wifi disable",
            "cmd connectivity airplane-mode enable",
            "cmd connectivity airplane-mode disable",
            "cmd uimode night yes",
            "cmd uimode night no",
            "cmd power set-mode 1",
            "cmd power set-mode 0",
            "cmd location set-location-enabled true",
            "cmd location set-location-enabled false",
            "input keyevent 26",
            "settings put system POWER_SAVE_MODE_OPEN 1",
            "settings put system POWER_SAVE_MODE_OPEN 0",
            "settings put global low_power 1",
            "settings put global low_power 0",
            "pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS",
        )
    }
}
