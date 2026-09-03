package com.flowpilot.app.actions

import com.flowpilot.app.BuildConfig

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

/** Shizuku wrapper. UserService executes commands under shell/root identity. */
class ShizukuShell private constructor() : ShizukuShellCompatible {
    companion object {
        val instance = ShizukuShell()
        const val REQUEST_PERMISSION_CODE = 1000
    }

    @Volatile private var appContext: Context? = null
    @Volatile private var commandService: ICommandService? = null
    private val bindLock = Any()
    @Volatile private var bindLatch: CountDownLatch? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            commandService = ICommandService.Stub.asInterface(binder)
            synchronized(bindLock) {
                bindLatch?.countDown()
                bindLatch = null
            }
        }
        override fun onServiceDisconnected(name: ComponentName) {
            synchronized(bindLock) {
                commandService = null
                bindLatch?.countDown()
                bindLatch = null
            }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky {
                try {
                    if (hasPermission()) {
                        ensureBound()
                    }
                } catch (_: Throwable) {}
            }
            Shizuku.addBinderDeadListener {
                synchronized(bindLock) {
                    commandService = null
                    bindLatch?.countDown()
                    bindLatch = null
                }
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
        val service = commandService ?: bindAndGetService() ?: return -1 to "Shizuku UserService unavailable"
        return try {
            val result = service.run(command)
            val newline = result.indexOf('\n')
            val pair = if (newline < 0) (-1 to result) else (result.substring(0, newline).toIntOrNull() ?: -1) to result.substring(newline + 1)
            android.util.Log.i("ShizukuShell", "Executed '$command' -> exitCode=${pair.first}, output='${pair.second}'")
            pair
        } catch (t: Throwable) {
            android.util.Log.e("ShizukuShell", "Command error: ${t.message}", t)
            synchronized(bindLock) {
                commandService = null
                bindLatch?.countDown()
                bindLatch = null
            }
            -1 to (t.message ?: t.javaClass.simpleName)
        }
    }

    private fun bindAndGetService(): ICommandService? {
        val latch = bindIfNeeded() ?: return null
        try {
            val bound = latch.await(2, TimeUnit.SECONDS)
            if (!bound) {
                synchronized(bindLock) {
                    if (commandService == null) {
                        bindLatch?.countDown()
                        bindLatch = null
                    }
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            synchronized(bindLock) {
                if (commandService == null) {
                    bindLatch?.countDown()
                    bindLatch = null
                }
            }
        }
        return commandService
    }

    private fun bindIfNeeded(): CountDownLatch? = synchronized(bindLock) {
        if (!isShizukuRunning() || !hasPermission()) return@synchronized null
        commandService?.let { return@synchronized CountDownLatch(0) }
        bindLatch?.let { return@synchronized it }
        val latch = CountDownLatch(1)
        bindLatch = latch
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(BuildConfig.APPLICATION_ID, CommandUserService::class.java.name),
            ).daemon(false).tag("flowpilot-command").version(BuildConfig.VERSION_CODE).debuggable(BuildConfig.DEBUG).processNameSuffix("command")
            Shizuku.bindUserService(args, connection)
            latch
        } catch (t: Throwable) {
            bindLatch = null
            android.util.Log.e("ShizukuShell", "bindUserService failed: ${t.message}", t)
            null
        }
    }
}

/** One-shot result channel from [MainActivity]'s Shizuku listener to the UI. */
object ShizukuPermissionBridge {
    @Volatile var onResult: ((Boolean) -> Unit)? = null
}
