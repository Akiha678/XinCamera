package com.seanchen.xincamera.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.seanchen.xincamera.nativebridge.NativeBridge
import java.util.concurrent.Executor

/**
 * CameraX ImageAnalysis 分析器。
 *
 * 这里只负责把预览帧转成 native 算法需要的数据，算法本身通过 NativeBridge 进入 C++。
 */
class Analyzer(
    private val mainExecutor: Executor,
    private val onHistogramChanged: (IntArray) -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastHistogramAtMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHistogramAtMs < HISTOGRAM_INTERVAL_MS) {
                return
            }
            lastHistogramAtMs = now

            val yPlane = image.planes.firstOrNull() ?: return
            val buffer = yPlane.buffer
            val yBytes = ByteArray(buffer.remaining())
            buffer.get(yBytes)

            val histogram = NativeBridge.computeLumaHistogram(
                yPlane = yBytes,
                width = image.width,
                height = image.height,
                rowStride = yPlane.rowStride,
                pixelStride = yPlane.pixelStride
            )
            mainExecutor.execute {
                onHistogramChanged(histogram)
            }
        } catch (error: Exception) {
            mainExecutor.execute {
                onError(error.message ?: "Histogram analysis failed")
            }
        } finally {
            image.close()
        }
    }

    private companion object {
        const val HISTOGRAM_INTERVAL_MS = 120L
    }
}
