package com.openring.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openring.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 圖示顯示尺寸，縮小以提升效能 */
private const val ICON_SIZE = 96

/**
 * 將 Drawable 轉為 ImageBitmap（縮小至 ICON_SIZE 以減少記憶體與處理負擔）
 */
private fun Drawable.toComposeImageBitmap(): ImageBitmap {
    val srcBitmap = when (this) {
        is BitmapDrawable -> bitmap ?: run {
            Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        }
        else -> {
            val w = intrinsicWidth.coerceAtLeast(1)
            val h = intrinsicHeight.coerceAtLeast(1)
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                this@toComposeImageBitmap.setBounds(0, 0, w, h)
                this@toComposeImageBitmap.draw(canvas)
            }
        }
    }
    return if (srcBitmap.width <= ICON_SIZE && srcBitmap.height <= ICON_SIZE) {
        srcBitmap.asImageBitmap()
    } else {
        Bitmap.createScaledBitmap(srcBitmap, ICON_SIZE, ICON_SIZE, true).asImageBitmap()
    }
}

/**
 * 全螢幕 App 選擇器，網格顯示圖示與名稱
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppPickerSheet(
    apps: List<Pair<String, String>>,
    selectedPackage: String?,
    onDismiss: () -> Unit,
    onSelectApp: (String) -> Unit,
    onSelectCustom: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var searchQuery by remember { mutableStateOf("") }
    val iconCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { (name, _) -> name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("選擇 App") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        placeholder = { Text("搜尋 App...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        singleLine = true
                    )

                    LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                contentPadding = PaddingValues(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredApps, key = { it.second }) { (appName, packageName) ->
                    var iconBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(iconCache[packageName]) }
                    LaunchedEffect(packageName) {
                        iconCache[packageName]?.let { iconBitmap = it; return@LaunchedEffect }
                        val bitmap = withContext(Dispatchers.Default) {
                            try {
                                pm.getApplicationIcon(packageName).toComposeImageBitmap()
                            } catch (_: Exception) { null }
                        }
                        if (bitmap != null) {
                            iconCache[packageName] = bitmap
                            iconBitmap = bitmap
                        }
                    }
                            val isSelected = packageName == selectedPackage

                            Column(
                                modifier = Modifier
                                    .clickable {
                                        onSelectApp(packageName)
                                        onDismiss()
                                    }
                                    .padding(Spacing.sm),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val bitmap = iconBitmap
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Image(
                                            bitmap = bitmap,
                                            contentDescription = appName,
                                            modifier = Modifier.size(56.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Apps,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = appName,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        item {
                            Column(
                                modifier = Modifier
                                    .clickable {
                                        onSelectCustom()
                                        onDismiss()
                                    }
                                    .padding(Spacing.sm),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "自訂 Package",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
