package com.openring.core

import android.content.Context
import android.content.Intent
import android.util.Log
import android.content.pm.PackageManager
import android.net.Uri
import com.openring.core.model.ActionResult
import com.openring.core.model.ErrorCode

/**
 * 喚醒 App、Deep Link 跳轉
 * US-1.6: 支援 package 與 custom scheme Deep Link
 */
class IntentRouter(private val context: Context) {

    /**
     * 透過 package 或 uri 喚醒目標 App
     */
    fun launchApp(packageName: String, uri: String? = null): ActionResult {
        Log.d("OpenRing", "IntentRouter: launchApp package=$packageName uri=$uri")
        if (!isAppInstalled(packageName)) {
            Log.e("OpenRing", "IntentRouter: App 未安裝 $packageName")
            return ActionResult.Error(ErrorCode.APP_NOT_INSTALLED, "App not installed: $packageName")
        }

        val intent = if (uri != null && uri.isNotBlank()) {
            try {
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            } catch (e: Exception) {
                return ActionResult.Error(ErrorCode.ACTION_FAILED, e.message)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return ActionResult.Error(ErrorCode.ACTION_FAILED, "No launcher activity")
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
            Log.d("OpenRing", "IntentRouter: startActivity 成功")
            return ActionResult.Ok
        } catch (e: Exception) {
            Log.e("OpenRing", "IntentRouter: startActivity 失敗", e)
            return ActionResult.Error(ErrorCode.ACTION_FAILED, e.message)
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
