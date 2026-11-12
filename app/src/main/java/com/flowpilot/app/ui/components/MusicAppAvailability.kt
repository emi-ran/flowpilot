package com.flowpilot.app.ui.components

import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flowpilot.app.actions.MusicLinkValidator

@Composable
internal fun rememberMusicAppAvailability(): (String) -> Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return remember(context, revision) {
        val available = listOf(MusicLinkValidator.SPOTIFY, MusicLinkValidator.YOUTUBE_MUSIC).filter { packageName ->
            try { context.packageManager.getApplicationInfo(packageName, 0).enabled }
            catch (_: PackageManager.NameNotFoundException) { false }
        }.toSet()
        val check: (String) -> Boolean = { packageName -> packageName in available }
        check
    }
}
