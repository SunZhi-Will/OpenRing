package com.openring.ui.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.openring.R

/**
 * Shared visual style for OpenRing notifications (icon, accent, no timestamp clutter).
 */
object OpenRingNotificationStyle {
    fun brandColor(context: Context): Int =
        ContextCompat.getColor(context, R.color.notification_brand)

    fun apply(builder: NotificationCompat.Builder, context: Context): NotificationCompat.Builder =
        builder
            .setSmallIcon(R.drawable.ic_stat_openring)
            .setColor(brandColor(context))
            .setShowWhen(false)
}
