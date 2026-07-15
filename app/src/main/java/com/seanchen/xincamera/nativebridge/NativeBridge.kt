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
}
