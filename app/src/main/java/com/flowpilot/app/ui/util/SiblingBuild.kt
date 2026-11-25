package com.flowpilot.app.ui.util

import android.content.Context
import android.content.pm.PackageManager

/**
 * Release (`com.flowpilot.app`) and debug (`com.flowpilot.app.debug`) builds can be installed side by side
 * and each runs its own engine, so the same trigger may fire the same rule twice. This only detects the
 * sibling install so the UI can warn; it does not talk to the other app.
 */
object SiblingBuild {
    private const val DEBUG_SUFFIX = ".debug"

    fun siblingPackageName(ownPackage: String): String =
        if (ownPackage.endsWith(DEBUG_SUFFIX)) ownPackage.removeSuffix(DEBUG_SUFFIX) else ownPackage + DEBUG_SUFFIX

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(siblingPackageName(context.packageName), 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: RuntimeException) {
        false
    }
}
