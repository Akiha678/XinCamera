package com.seanchen.xincamera.nativebridge

object NativeBridge {
    init {
        System.loadLibrary("xincamera")
    }

    external fun nativePreviewPipelineName(): String

    /**
     * 计算 YUV_420_888 预览帧的亮度直方图。
     *
     * 只读取 Y 平面即可得到亮度分布，返回固定 256 个桶，索引 0 表示最暗，255 表示最亮。
     */
    external fun computeLumaHistogram(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): IntArray

    /**
     * 将 ARGB_8888 像素数组转换为灰度图。
     *
     * Kotlin 侧负责 Bitmap 解码和保存；JNI/C++ 侧只处理像素计算，便于后续继续扩展滤镜算法。
     * 输入和输出都使用 Android ColorInt 格式：0xAARRGGBB。
     */
    external fun applyGrayscaleArgb8888(
        pixels: IntArray,
        width: Int,
        height: Int
    ): IntArray
}
