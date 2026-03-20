package com.openring.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Design tokens for consistent spacing and sizing across the app.
 * 設計 token，確保全 app 間距與尺寸一致
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

/** 統一圓角 */
object Shapes {
    val card = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(8.dp)
    val input = RoundedCornerShape(8.dp)
}
