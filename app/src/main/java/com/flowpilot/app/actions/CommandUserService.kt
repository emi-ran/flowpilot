package com.flowpilot.app.actions

import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Shizuku UserService process. Runs as shell (ADB backend) or root (root backend). */
class CommandUserService : ICommandService.Stub() {
    override fun run(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.forEachLine { output.appendLine(it) }
            }
            val error = BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                reader.readText()
            }
            if (error.isNotBlank()) output.append(error)
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return "124\ncommand timed out"
            }
            "${process.exitValue()}\n${output.toString().trim()}"
        } catch (t: Throwable) {
            "125\n${t.message ?: t.javaClass.simpleName}"
        }
    }
}
