package com.openring.localmodel

import android.os.Build

object LocalModelSupport {
    fun isSupportedDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val device = Build.DEVICE.lowercase()
        if (manufacturer == "google" && device == "panther") {
            return false
        }
        return Build.SUPPORTED_64_BIT_ABIS.any { it.contains("arm64") }
    }

    fun unsupportedReason(): String {
        return "此裝置目前停用本機模型（已知會造成原生層閃退），請改用雲端模型。"
    }
}
