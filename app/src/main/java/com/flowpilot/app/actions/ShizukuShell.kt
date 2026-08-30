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

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            commandService = ICommandService.Stub.asInterface(binder)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            commandService = null
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky {
                if (hasPermission()) {
                    ensureBound()
                }
            }
            Shizuku.addBinderDeadListener {
                commandService = null
            }
        } catch (_: Throwable) {}
    }

    fun isShizukuAvailable(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
    override fun isShizukuRunning(): Boolean = isShizukuAvailable()
    override fun hasPermission(): Boolean = isShizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
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

    fun ensureBound() {
        if (!isShizukuRunning() || !hasPermission() || commandService != null) return
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(BuildConfig.APPLICATION_ID, CommandUserService::class.java.name),
            ).daemon(false).tag("flowpilot-command").version(BuildConfig.VERSION_CODE).debuggable(BuildConfig.DEBUG).processNameSuffix("command")
            android.util.Log.d("ShizukuShell", "Calling Shizuku.bindUserService")
            Shizuku.bindUserService(args, connection)
        } catch (t: Throwable) {
            android.util.Log.e("ShizukuShell", "ensureBound error: ${t.message}", t)
        }
    }

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
            commandService = null
            -1 to (t.message ?: t.javaClass.simpleName)
        }
    }

    private fun bindAndGetService(): ICommandService? = synchronized(bindLock) {
        commandService?.let { return@synchronized it }
        val latch = CountDownLatch(1)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                android.util.Log.d("ShizukuShell", "UserService connected: $name")
                commandService = ICommandService.Stub.asInterface(binder)
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) {
                android.util.Log.d("ShizukuShell", "UserService disconnected: $name")
                commandService = null
            }
        }
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(BuildConfig.APPLICATION_ID, CommandUserService::class.java.name),
            ).daemon(false).tag("flowpilot-command").version(BuildConfig.VERSION_CODE).debuggable(BuildConfig.DEBUG).processNameSuffix("command")
            android.util.Log.d("ShizukuShell", "bindAndGetService: binding...")
            Shizuku.bindUserService(args, conn)
            val ok = latch.await(5, TimeUnit.SECONDS)
            android.util.Log.d("ShizukuShell", "bindAndGetService: await result=$ok, service=${commandService != null}")
            commandService
        } catch (t: Throwable) {
            android.util.Log.e("ShizukuShell", "bindAndGetService failed: ${t.message}", t)
            null
        }
    }
}

/** One-shot result channel from [MainActivity]'s Shizuku listener to the UI. */
object ShizukuPermissionBridge {
    @Volatile var onResult: ((Boolean) -> Unit)? = null
}
