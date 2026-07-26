package com.seanchen.xincamera.nativebridge

/**
 * Native 能力对 Kotlin 业务层暴露的稳定入口。
 *
 * 企业项目里不建议让业务代码直接调用 `external fun`，因为 JNI 签名和 C++ 导出函数强绑定。
 * 这一层作为 facade 隔离调用方和 native 方法声明，后续 C++ 函数改名、拆库或增加错误处理时，
 * Camera/UI 层不需要跟着改。
 */
object NativeBridge {
    fun nativePreviewPipelineName(): String = NativeMethods.nativePreviewPipelineName()

    /**
     * 计算 YUV_420_888 预览帧的亮度直方图。
     *
     * 只读取 Y 平面即可得到亮度分布，返回固定 256 个桶，索引 0 表示最暗，255 表示最亮。
     */
    fun computeLumaHistogram(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): IntArray {
        if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) {
            return IntArray(HISTOGRAM_BUCKET_COUNT)
        }
        return NativeMethods.computeLumaHistogram(
            yPlane = yPlane,
            width = width,
            height = height,
            rowStride = rowStride,
            pixelStride = pixelStride
        )
    }

    /**
     * 将 ARGB_8888 像素数组转换为灰度图。
     *
     * Kotlin 侧负责 Bitmap 解码和保存；JNI/C++ 侧只处理像素计算，便于后续继续扩展滤镜算法。
     * 输入和输出都使用 Android ColorInt 格式：0xAARRGGBB。
     */
    fun applyGrayscaleArgb8888(
        pixels: IntArray,
        width: Int,
        height: Int
    ): IntArray {
        val expectedSize = width.toLong() * height.toLong()
        if (width <= 0 || height <= 0 || expectedSize <= 0L || pixels.size.toLong() < expectedSize) {
            return IntArray(0)
        }
        return NativeMethods.applyGrayscaleArgb8888(
            pixels = pixels,
            width = width,
            height = height
        )
    }

    private const val HISTOGRAM_BUCKET_COUNT = 256
}
