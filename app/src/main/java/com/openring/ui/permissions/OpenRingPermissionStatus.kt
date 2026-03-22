package com.openring.ui.permissions

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.openring.core.OpenRingAccessibilityService

/** Whether OpenRing's accessibility service is enabled in system settings. */
fun isOpenRingAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, OpenRingAccessibilityService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    while (splitter.hasNext()) {
        val cn = ComponentName.unflattenFromString(splitter.next())
        if (cn != null && cn == expected) return true
    }
    return false
}
