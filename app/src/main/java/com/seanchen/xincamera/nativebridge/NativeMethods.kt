package com.seanchen.xincamera.nativebridge

/**
 * JNI 方法声明层。
 *
 * 这个类只放 `external fun`，不承载业务逻辑。C++ 的 JNI 导出函数只需要对齐这里的
 * 包名、类名和方法名，调用方则通过 `NativeBridge` facade 间接访问。
 */
internal object NativeMethods {
    init {
        NativeLibraryLoader.load()
    }

    external fun nativePreviewPipelineName(): String

    external fun computeLumaHistogram(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): IntArray

    external fun applyGrayscaleArgb8888(
        pixels: IntArray,
        width: Int,
        height: Int
    ): IntArray
}
