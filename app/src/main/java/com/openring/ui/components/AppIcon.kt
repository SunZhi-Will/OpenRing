package com.openring.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CACHE_SIZE = 64

private fun Drawable.toImageBitmap(size: Int): ImageBitmap {
    val srcBitmap = when (this) {
        is BitmapDrawable -> bitmap ?: run {
            Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        }
        else -> {
            val w = intrinsicWidth.coerceAtLeast(1)
            val h = intrinsicHeight.coerceAtLeast(1)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                this@toImageBitmap.setBounds(0, 0, w, h)
                this@toImageBitmap.draw(canvas)
            }
        }
    }
    return if (srcBitmap.width <= size && srcBitmap.height <= size) {
        srcBitmap.asImageBitmap()
    } else {
        Bitmap.createScaledBitmap(srcBitmap, size, size, true).asImageBitmap()
    }
}

/**
 * 非同步載入並顯示 App 圖示
 */
@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    showBackground: Boolean = true
) {
    val context = LocalContext.current
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        bitmap = withContext(Dispatchers.Default) {
            try {
                context.packageManager.getApplicationIcon(packageName).toImageBitmap(CACHE_SIZE)
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showBackground) Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when (val b = bitmap) {
            null -> Icon(
                Icons.Default.Apps,
                contentDescription = null,
                modifier = Modifier.size(size * 0.6f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier.size(size * 0.8f),
                contentScale = ContentScale.Fit
            )
        }
    }
}
