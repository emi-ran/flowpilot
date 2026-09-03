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
        if (command !in allowedCommands) {
            return "126\ncommand not allowed"
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = StringBuilder()
            val inThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { r ->
                        r.forEachLine { synchronized(output) { output.appendLine(it) } }
                    }
                } catch (_: Throwable) {}
            }
            val errThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).use { r ->
                        r.forEachLine { synchronized(output) { output.appendLine(it) } }
                    }
                } catch (_: Throwable) {}
            }
            inThread.start()
            errThread.start()

            val finished = process.waitFor(10, TimeUnit.SECONDS)
            inThread.join(1000)
            errThread.join(1000)

            if (!finished) {
                process.destroyForcibly()
                return "124\ncommand timed out"
            }
            val text = synchronized(output) { output.toString().trim() }
            "${process.exitValue()}\n$text"
        } catch (t: Throwable) {
            "125\n${t.message ?: t.javaClass.simpleName}"
        }
    }

    private companion object {
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
            "settings put system POWER_SAVE_MODE_OPEN 1",
            "settings put system POWER_SAVE_MODE_OPEN 0",
            "settings put global low_power 1",
            "settings put global low_power 0",
            "pm grant ${BuildConfig.APPLICATION_ID} android.permission.WRITE_SECURE_SETTINGS",
        )
    }
}
