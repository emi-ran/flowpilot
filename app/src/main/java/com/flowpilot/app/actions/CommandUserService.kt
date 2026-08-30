package com.flowpilot.app.actions

import android.content.Context
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

@Keep
class CommandUserService : ICommandService.Stub {
    constructor() : super()
    constructor(context: Context) : super()

    override fun run(command: String): String {
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
}
