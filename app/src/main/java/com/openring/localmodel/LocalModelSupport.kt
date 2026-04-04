package com.openring.localmodel

import android.os.Build
import android.util.Log

object LocalModelSupport {
    private const val TAG = "OpenRing"

    /**
     * Pixel 7（`panther`）曾於舊版 JNI 路徑出現原生閃退，先前暫時封鎖；已重新開放並於 [LocalLlmEngine] 加強診斷 log。
     * 若仍崩潰，請擷取 logcat 中 `LocalLlmDiag` / `OpenRing` 最後幾行對照 tombstone。
     */
    @Volatile
    private var loggedPantherExperimental: Boolean = false

    /**
     * 與 [com.openring] `ndk.abiFilters`（arm64-v8a + x86_64）對齊：實機多為 arm64，Android 模擬器多為 x86_64。
     * 先前僅檢查 `arm64`，會讓模擬器使用者無法在設定裡切到「地端模型」。
     */
    fun isSupportedDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val device = Build.DEVICE.lowercase()
        if (manufacturer == "google" && device == "panther" && !loggedPantherExperimental) {
            loggedPantherExperimental = true
            Log.w(
                TAG,
                "LocalLlmDiag device=panther (Pixel 7): local GGUF re-enabled (experimental). " +
                    "If SIGSEGV, capture: adb logcat -b crash -b main -d | tail -200"
            )
        }
        val abis64 = Build.SUPPORTED_64_BIT_ABIS
        if (abis64.isEmpty()) return false
        return abis64.any { abi ->
            abi.contains("arm64", ignoreCase = true) ||
                abi.equals("x86_64", ignoreCase = true)
        }
    }

    fun unsupportedReason(): String {
        return "此裝置目前不支援本機 GGUF（需 64 位元 arm64 或 x86_64；部分機型已知會原生閃退已停用），請改用雲端模型。"
    }
}
