package com.openring.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Display
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 透過 [AccessibilityService.takeScreenshot]（API 30+）取得畫面，縮圖並輸出 JPEG Base64，供視覺模型使用。
 * WebView／遊戲等無完整無障礙樹時，可作為備援感知路徑。
 */
object ScreenCapture {

    /**
     * @param maxWidth 最大寬度（px），過大畫面會等比縮小以降低傳輸與 token
     * @return JPEG Base64（無換行），失敗時為 null（API&lt;30、截圖失敗等）
     */
    fun captureJpegBase64(
        service: AccessibilityService,
        maxWidth: Int = 720,
        jpegQuality: Int = 82
    ): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val latch = CountDownLatch(1)
        val holder = arrayOfNulls<String>(1)
        val main = Handler(Looper.getMainLooper())
        main.post {
            try {
                val exec = Executors.newSingleThreadExecutor()
                service.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    exec,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                val hb: HardwareBuffer? = screenshot.hardwareBuffer
                                if (hb == null) {
                                    holder[0] = null
                                    return
                                }
                                val raw = Bitmap.wrapHardwareBuffer(hb, null)
                                hb.close()
                                if (raw == null) {
                                    holder[0] = null
                                    return
                                }
                                val safe = raw.copy(Bitmap.Config.ARGB_8888, false) ?: raw
                                if (safe != raw) raw.recycle()
                                val scaled = scaleDown(safe, maxWidth)
                                if (scaled != safe) safe.recycle()
                                val os = ByteArrayOutputStream()
                                if (!scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, os)) {
                                    scaled.recycle()
                                    holder[0] = null
                                    return
                                }
                                scaled.recycle()
                                holder[0] = Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP)
                            } catch (_: Exception) {
                                holder[0] = null
                            } finally {
                                latch.countDown()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            holder[0] = null
                            latch.countDown()
                        }
                    }
                )
            } catch (_: Exception) {
                holder[0] = null
                latch.countDown()
            }
        }
        latch.await(22, TimeUnit.SECONDS)
        return holder[0]
    }

    private fun scaleDown(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidth, h, true)
    }
}
