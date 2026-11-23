package com.flowpilot.app.actions

import com.flowpilot.app.BuildConfig

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/** Shizuku wrapper. UserService executes commands under shell/root identity. */
class ShizukuShell private constructor() : ShizukuShellCompatible {
    companion object {
        val instance = ShizukuShell()
        const val REQUEST_PERMISSION_CODE = 1000
        private const val HOTSPOT_TIMEOUT_SECONDS = 15L
        private const val APP_PROCESS = "/system/bin/app_process"
        private const val HOTSPOT_BRIDGE_CLASS = "com.flowpilot.app.actions.HotspotBridge"
        private const val USER_SERVICE_RETRY_MS = 60_000L
        private const val COMMAND_TIMEOUT_SECONDS = 10L
    }

    @Volatile private var appContext: Context? = null
    @Volatile private var commandService: ICommandService? = null
    @Volatile private var userServiceUnavailableUntilMs = 0L
    private val bindLock = Any()
    @Volatile private var bindLatch: CountDownLatch? = null
    /** The single in-flight or established UserService binding; guarded by [bindLock]. */
    private var activeConnection: ServiceConnection? = null

    private fun newConnection(): ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            synchronized(bindLock) {
                // A connection that was already torn down (timeout, failure) must not leak a service.
                if (activeConnection !== this) return
                commandService = ICommandService.Stub.asInterface(binder)
                bindLatch?.countDown()
                bindLatch = null
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            resetBind(this)
        }
    }

    private fun userServiceArgs(): Shizuku.UserServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, CommandUserService::class.java.name),
    ).daemon(false).tag("flowpilot-command").version(BuildConfig.VERSION_CODE).debuggable(BuildConfig.DEBUG).processNameSuffix("command")

    /**
     * Clears bind state and unbinds the Shizuku UserService (remove=true) so the
     * `:command` process does not outlive the binding. When [only] is given, acts only if it is still current.
     */
    private fun resetBind(only: ServiceConnection? = null) {
        val stale: ServiceConnection?
        synchronized(bindLock) {
            if (only != null && activeConnection !== only) return
            stale = activeConnection
            activeConnection = null
            commandService = null
            bindLatch?.countDown()
            bindLatch = null
        }
        if (stale != null) {
            try {
                Shizuku.unbindUserService(userServiceArgs(), stale, true)
            } catch (_: Throwable) {
                // Shizuku may be gone already; its death also ends the user service.
            }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky {
                try {
                    userServiceUnavailableUntilMs = 0L
                    if (hasPermission()) {
                        ensureBound()
                    }
                } catch (_: Throwable) {}
            }
            Shizuku.addBinderDeadListener {
                resetBind()
            }
        } catch (_: Throwable) {}
    }

    fun isShizukuAvailable(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
    override fun isShizukuRunning(): Boolean = isShizukuAvailable()
    override fun hasPermission(): Boolean = try {
        isShizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun canRequestPermission(): Boolean = try {
        isShizukuRunning() && !hasPermission() && !Shizuku.shouldShowRequestPermissionRationale()
    } catch (_: Throwable) { false }

    /** Ask Shizuku to show its grant dialog. Returns false when the dialog cannot be shown right now. */
    fun requestPermission(): Boolean = try {
        if (isShizukuRunning() && !Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(REQUEST_PERMISSION_CODE)
            true
        } else {
            false
        }
    } catch (_: Throwable) { false }

    fun ensureBound() = bindIfNeeded()

    override fun run(command: String): Pair<Int, String> {
        if (!isShizukuRunning()) {
            android.util.Log.w("ShizukuShell", "run: Shizuku not running")
            return -1 to "Shizuku not running"
        }
        if (!hasPermission()) {
            android.util.Log.w("ShizukuShell", "run: Shizuku permission not granted")
            return -1 to "Shizuku permission not granted"
        }
        val service = commandService ?: bindAndGetService()
        if (service == null) {
            android.util.Log.w("ShizukuShell", "run: UserService unavailable, trying newProcess")
            return runViaNewProcess(command)
        }
        return try {
            val result = service.run(command)
            val newline = result.indexOf('\n')
            val pair = if (newline < 0) (-1 to result) else (result.substring(0, newline).toIntOrNull() ?: -1) to result.substring(newline + 1)
            android.util.Log.i("ShizukuShell", "Command completed: exitCode=${pair.first}")
            pair
        } catch (_: Throwable) {
            android.util.Log.e("ShizukuShell", "Command execution failed")
            resetBind()
            -1 to "Shizuku command execution failed"
        }
    }

    /**
     * Runs [HotspotBridge] under the shell UID through Shizuku's remote process API.
     *
     * The tethering service only accepts Wi-Fi tethering from the tethering owner, and
     * `cmd wifi` is rejected with a SecurityException, so the bridge is started with
     * app_process against this app's base APK. It intentionally bypasses the UserService
     * command allowlist: the argument list is fixed here and the only caller input is a Boolean.
     */
    override fun runHotspot(enabled: Boolean): Pair<Int, String> {
        if (!isShizukuRunning()) return -1 to "Shizuku not running"
        if (!hasPermission()) return -1 to "Shizuku permission not granted"

        val apkPath = appContext?.packageCodePath
        if (apkPath.isNullOrBlank()) {
            android.util.Log.w("ShizukuShell", "runHotspot: application context unavailable")
            return -1 to "Application context unavailable"
        }

        return runRemoteProcess(
            arrayOf(
                APP_PROCESS,
                "-Djava.class.path=$apkPath",
                "/system/bin",
                HOTSPOT_BRIDGE_CLASS,
                if (enabled) "on" else "off",
            ),
            HOTSPOT_TIMEOUT_SECONDS,
            "Hotspot bridge",
            "hotspot bridge timed out",
            "Shizuku hotspot execution failed",
        )
    }

    /**
     * Runs an allowlisted shell command through Shizuku's remote process API when the
     * UserService cannot start (e.g. Android 16 LoadedApk crash in Shizuku 13.6.0).
     */
    private fun runViaNewProcess(command: String): Pair<Int, String> {
        if (!CommandUserService.isCommandAllowed(command)) {
            return 126 to "command not allowed"
        }
        // The allowlist has no shell metacharacters, so `exec` makes sh become the command
        // and process.destroy() reaches the real process instead of only the wrapper shell.
        return runRemoteProcess(
            arrayOf("sh", "-c", "exec $command"),
            COMMAND_TIMEOUT_SECONDS,
            "Fallback command",
            "command timed out",
            "Shizuku command execution failed",
        )
    }

    /** Runs a Shizuku remote process; the process is always destroyed on every exit path. */
    private fun runRemoteProcess(
        argv: Array<String>,
        timeoutSeconds: Long,
        logLabel: String,
        timeoutMessage: String,
        failureMessage: String,
    ): Pair<Int, String> {
        var process: IRemoteProcess? = null
        return try {
            val binder = Shizuku.getBinder() ?: return -1 to "Shizuku not running"
            val started = IShizukuService.Stub.asInterface(binder).newProcess(argv, null, null)
            process = started
            val output = StringBuilder()
            val outThread = Thread { readStream(started.inputStream, output) }.apply { isDaemon = true }
            val errThread = Thread { readStream(started.errorStream, output) }.apply { isDaemon = true }
            outThread.start()
            errThread.start()

            val finished = started.waitForTimeout(timeoutSeconds, TimeUnit.SECONDS.name)
            if (!finished) {
                started.destroy()
            }
            outThread.join(1000)
            errThread.join(1000)

            if (!finished) {
                return 124 to timeoutMessage
            }
            val exitCode = started.exitValue()
            android.util.Log.i("ShizukuShell", "$logLabel completed: exitCode=$exitCode")
            exitCode to synchronized(output) { output.toString().trim() }
        } catch (t: Throwable) {
            if (t is InterruptedException) Thread.currentThread().interrupt()
            android.util.Log.e("ShizukuShell", "$logLabel execution failed: ${t.javaClass.simpleName}")
            -1 to failureMessage
        } finally {
            try { process?.destroy() } catch (_: Throwable) {}
        }
    }

    private fun readStream(descriptor: ParcelFileDescriptor, output: StringBuilder) {
        try {
            BufferedReader(
                InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(descriptor)),
            ).use { reader ->
                reader.forEachLine { line ->
                    synchronized(output) { output.appendLine(line) }
                }
            }
        } catch (_: Throwable) {
            // Output is best-effort; the exit code remains authoritative.
        }
    }

    private fun bindAndGetService(): ICommandService? {
        val latch = bindIfNeeded() ?: return null
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                cancelPendingBind(latch)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            cancelPendingBind(latch)
        }
        return commandService.also {
            if (it == null) {
                userServiceUnavailableUntilMs = System.currentTimeMillis() + USER_SERVICE_RETRY_MS
            }
        }
    }

    /** Cancels a bind that did not complete in time so the pending request cannot connect later and leak. */
    private fun cancelPendingBind(latch: CountDownLatch) {
        val pending = synchronized(bindLock) {
            if (commandService == null && bindLatch === latch) activeConnection else null
        }
        if (pending != null) resetBind(pending)
    }

    private fun bindIfNeeded(): CountDownLatch? {
        val connection: ServiceConnection
        val latch: CountDownLatch
        synchronized(bindLock) {
            if (!isShizukuRunning() || !hasPermission()) return null
            if (System.currentTimeMillis() < userServiceUnavailableUntilMs) return null
            commandService?.let { return CountDownLatch(0) }
            bindLatch?.let { return it }
            connection = newConnection()
            latch = CountDownLatch(1)
            activeConnection = connection
            bindLatch = latch
        }
        return try {
            Shizuku.bindUserService(userServiceArgs(), connection)
            latch
        } catch (_: Throwable) {
            resetBind(connection)
            android.util.Log.e("ShizukuShell", "User service binding failed")
            null
        }
    }
}

/** One-shot result channel from [MainActivity]'s Shizuku listener to the UI. */
object ShizukuPermissionBridge {
    @Volatile var onResult: ((Boolean) -> Unit)? = null
}
