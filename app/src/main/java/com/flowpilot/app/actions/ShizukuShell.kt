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

    fun init(context: Context) { appContext = context.applicationContext }
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

    override fun run(command: String): Pair<Int, String> {
        if (!isShizukuRunning() || !hasPermission()) return -1 to "Shizuku permission unavailable"
        val service = commandService ?: bindAndGetService() ?: return -1 to "Shizuku UserService unavailable"
        return try {
            val result = service.run(command)
            val newline = result.indexOf('\n')
            if (newline < 0) (-1 to result) else (result.substring(0, newline).toIntOrNull() ?: -1) to result.substring(newline + 1)
        } catch (t: Throwable) {
            commandService = null
            -1 to (t.message ?: t.javaClass.simpleName)
        }
    }

    private fun bindAndGetService(): ICommandService? = synchronized(bindLock) {
        commandService?.let { return@synchronized it }
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                commandService = ICommandService.Stub.asInterface(binder)
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { commandService = null }
        }
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(BuildConfig.APPLICATION_ID, CommandUserService::class.java.name),
            ).daemon(false).tag("flowpilot-command").version(BuildConfig.VERSION_CODE).debuggable(BuildConfig.DEBUG)
            Shizuku.bindUserService(args, connection)
            latch.await(5, TimeUnit.SECONDS)
            commandService
        } catch (_: Throwable) {
            null
        }
    }
}

/** One-shot result channel from [MainActivity]'s Shizuku listener to the UI. */
object ShizukuPermissionBridge {
    @Volatile var onResult: ((Boolean) -> Unit)? = null
}
