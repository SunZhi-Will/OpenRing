package com.openring.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * 取得裝置上已安裝且具啟動器的 App 列表
 * 需在 AndroidManifest 宣告 queries intent (MAIN/LAUNCHER)
 */
object InstalledAppsProvider {

    /**
     * @return List of (顯示名稱, packageName)，依名稱排序
     */
    fun getInstalledLauncherApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList: List<ResolveInfo> = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return resolveList
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString().trim()
                if (label.isNotEmpty() && pkg != context.packageName) {
                    label to pkg
                } else null
            }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
    }
}
