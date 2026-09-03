package com.flowpilot.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the real application launcher icon loaded dynamically from Android PackageManager,
 * with smooth caching and fallback to a Material vector icon.
 */
@Composable
fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier.size(28.dp),
    fallbackIcon: ImageVector = Icons.Default.Apps,
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        if (packageName.isBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val icon = appInfo.loadIcon(pm)
                val bmp = icon.toBitmap().asImageBitmap()
                withContext(Dispatchers.Main) {
                    bitmap = bmp
                }
            } catch (_: Throwable) {
                withContext(Dispatchers.Main) {
                    bitmap = null
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
        )
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            modifier = modifier,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
