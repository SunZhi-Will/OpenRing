package com.openring.localmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.codeshipping.llamakotlin.LlamaConfig;
import org.codeshipping.llamakotlin.LlamaNative;

/**
 * Kotlin 無法直接呼叫 llama-kotlin-android 內的 internal {@link LlamaNative}；
 * Java 可連結 public static native。所有 JNI 呼叫應在 {@link LocalLlmEngine} 的單一執行緒上進行。
 */
public final class LocalLlamaJni {
    private LocalLlamaJni() {}

    public interface TokenSink {
        void onToken(@NonNull String token);
    }

    public static void ensureLoaded() {
        LlamaNative.INSTANCE.ensureLoaded();
    }

    /** llama-android / llama.cpp build string for logcat diagnostics. */
    @Nullable
    public static String getNativeLibraryVersion() {
        try {
            ensureLoaded();
            return LlamaNative.nativeGetVersion();
        } catch (Throwable t) {
            return null;
        }
    }

    public static long nativeCreateContext() {
        return LlamaNative.nativeCreateContext();
    }

    public static void nativeDestroyContext(long handle) {
        LlamaNative.nativeDestroyContext(handle);
    }

    public static boolean nativeLoadModel(long handle, @NonNull String path, @NonNull LlamaConfig config) {
        LlamaNative.NativeConfig nc = LlamaNative.NativeConfig.Companion.fromLlamaConfig(config);
        return LlamaNative.nativeLoadModel(handle, path, nc);
    }

    public static boolean nativeIsModelLoaded(long handle) {
        return LlamaNative.nativeIsModelLoaded(handle);
    }

    @Nullable
    public static String nativeGetLastError(long handle) {
        return LlamaNative.nativeGetLastError(handle);
    }

    @NonNull
    public static String nativeGenerate(long handle, @NonNull String prompt, @SuppressWarnings("unused") @NonNull LlamaConfig config) {
        String r = LlamaNative.nativeGenerate(handle, prompt, null);
        return r != null ? r : "";
    }

    /**
     * Pass {@code null} as the last JNI arg so {@code LlamaContextWrapper} uses {@code currentConfig_}
     * from {@link #nativeLoadModel} (sampling + maxTokens). Avoids a second {@code configFromJava} pass
     * on generate; Pixel 7 logs showed {@code maxPromptTokens=12} with correct Kotlin {@code maxTokens=32},
     * consistent with mis-read or split config on the stream path.
     */
    public static void nativeGenerateStream(
            long handle,
            @NonNull String prompt,
            @NonNull TokenSink sink,
            @SuppressWarnings("unused") @NonNull LlamaConfig config
    ) {
        LlamaNative.nativeGenerateStream(handle, prompt, new LlamaNative.NativeTokenCallback() {
            @Override
            public void onToken(String token) {
                sink.onToken(token != null ? token : "");
            }
        }, null);
    }

    public static void nativeCancelGeneration(long handle) {
        LlamaNative.nativeCancelGeneration(handle);
    }
}
