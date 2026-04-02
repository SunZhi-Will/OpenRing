package com.openring.localmodel

import android.os.Build

object LocalModelSupport {
    /**
     * 與 [com.openring] `ndk.abiFilters`（arm64-v8a + x86_64）對齊：實機多為 arm64，Android 模擬器多為 x86_64。
     * 先前僅檢查 `arm64`，會讓模擬器使用者無法在設定裡切到「地端模型」。
     */
    fun isSupportedDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val device = Build.DEVICE.lowercase()
        if (manufacturer == "google" && device == "panther") {
            return false
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
