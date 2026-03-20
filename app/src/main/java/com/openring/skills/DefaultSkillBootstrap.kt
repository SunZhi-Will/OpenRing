package com.openring.skills

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 首次啟動自動匯入預設 Skill（例如 Threads）。
 *
 * 目標：讓使用者不用先 ZIP 匯入，也能在 UI 勾選啟用/停用。
 * 從 assets/skills 目錄中將預先打包的 skills 複製到本地的 filesDir/skills。
 */
object DefaultSkillBootstrap {
    private const val TAG = "DefaultSkillBootstrap"
    private const val THREADS_SKILL_ID = "threads"

    fun apply(context: Context) {
        val installedStore = InstalledSkillStore(context)
        val enabledStore = SkillEnabledStore(context)

        val installedIds = installedStore.getInstalledIds().toSet()
        var threadsJustInstalled = false

        if (!installedIds.contains(THREADS_SKILL_ID)) {
            val success = copySkillFromAssets(context, THREADS_SKILL_ID)
            if (success) {
                installedStore.addInstalled(THREADS_SKILL_ID)
                threadsJustInstalled = true
            } else {
                Log.e(TAG, "Failed to bootstrap default skill: $THREADS_SKILL_ID")
            }
        }

        // 預設情況把 threads 開啟；但如果使用者已經取消勾選（enabledIds 非空且未包含 threads），就不覆蓋。
        val enabledIds = enabledStore.getEnabledIds().toSet()
        val shouldEnableDefault = threadsJustInstalled || enabledIds.isEmpty()
        if (shouldEnableDefault && !enabledStore.isEnabled(THREADS_SKILL_ID)) {
            enabledStore.setEnabled(THREADS_SKILL_ID, true)
        }
    }

    private fun copySkillFromAssets(context: Context, skillId: String): Boolean {
        return try {
            val assetManager = context.assets
            val assetDir = "skills/$skillId"
            
            // 確認 assets 中是否有該 skill 的目錄/檔案
            val files = assetManager.list(assetDir)
            if (files.isNullOrEmpty()) {
                Log.e(TAG, "No files found in assets/$assetDir")
                return false
            }

            val targetDir = File(context.filesDir, "skills/$skillId")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            for (fileName in files) {
                val input = assetManager.open("$assetDir/$fileName")
                val output = File(targetDir, fileName).outputStream()
                input.copyTo(output)
                input.close()
                output.close()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying skill from assets: ${e.message}", e)
            false
        }
    }
}

