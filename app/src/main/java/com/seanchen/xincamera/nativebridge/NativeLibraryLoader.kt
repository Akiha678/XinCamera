package com.seanchen.xincamera.nativebridge

/**
 * Native 动态库加载器。
 *
 * 单独封装 `System.loadLibrary` 可以避免多个 native 类重复加载，也便于后续加入
 * ABI 检查、灰度发布、崩溃上报或 fallback 策略。
 */
internal object NativeLibraryLoader {
    private const val LIBRARY_NAME = "xincamera"

    @Volatile
    private var isLoaded = false

    fun load() {
        if (isLoaded) {
            return
        }
        synchronized(this) {
            if (!isLoaded) {
                System.loadLibrary(LIBRARY_NAME)
                isLoaded = true
            }
        }
    }
}
