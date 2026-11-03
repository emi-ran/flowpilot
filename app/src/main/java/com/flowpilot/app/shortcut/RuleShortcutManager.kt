package com.flowpilot.app.shortcut

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.flowpilot.app.R
import com.flowpilot.app.data.model.Automation

/**
 * Creates and removes pinned launcher shortcuts that open a single rule's manual run
 * confirmation. The shortcut carries only the rule id; the activity reloads the current rule.
 */
object RuleShortcutManager {

    const val ACTION_RUN_RULE = "com.flowpilot.app.action.RUN_RULE"
    const val EXTRA_RULE_ID = "com.flowpilot.app.extra.RULE_ID"

    sealed interface PinResult {
        data object Requested : PinResult
        data object PermissionDenied : PinResult
        data object Unsupported : PinResult
    }

    fun requestPin(context: Context, rule: Automation): PinResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return PinResult.Unsupported
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return PinResult.Unsupported
        if (isMiuiShortcutPermissionDenied(context)) return PinResult.PermissionDenied
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(rule.id))
            .setShortLabel(rule.normalizedName.take(10))
            .setLongLabel(rule.normalizedName)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(
                Intent(context, RuleShortcutActivity::class.java)
                    .setAction(ACTION_RUN_RULE)
                    .putExtra(EXTRA_RULE_ID, rule.id),
            )
            .setLongLived(true)
            .build()
        if (ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)) return PinResult.Requested
        return if (isXiaomi()) PinResult.PermissionDenied else PinResult.Unsupported
    }

    fun remove(context: Context, ruleId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(shortcutId(ruleId)))
        return true
    }

    fun isPinned(context: Context, ruleId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return false
        return manager.pinnedShortcuts.any { it.id == shortcutId(ruleId) }
    }

    /**
     * HyperOS routes pinned shortcuts through MIUI app-op 10017 and silently refuses the
     * request when it is denied. The numeric AppOpsManager overload is an unsupported but
     * accessible hidden API, so reflection is used with a safe fallback.
     */
    private fun isMiuiShortcutPermissionDenied(context: Context): Boolean {
        if (!isXiaomi()) return false
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            miuiCheckOpNoThrow.invoke(appOps, MIUI_SHORTCUT_OP, Process.myUid(), context.packageName) as Int
        }.getOrNull() ?: return false
        return mode == AppOpsManager.MODE_IGNORED || mode == AppOpsManager.MODE_ERRORED
    }

    private val miuiCheckOpNoThrow: java.lang.reflect.Method by lazy {
        AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
        )
    }

    private fun isXiaomi(): Boolean = Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)

    fun openPermissionSettings(context: Context) {
        val miui = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (miui.resolveActivity(context.packageManager) != null) {
                context.startActivity(miui)
                return
            }
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun shortcutId(ruleId: String): String = "rule_$ruleId"

    private const val MIUI_SHORTCUT_OP = 10017
}
