#include <jni.h>
#include <cstdint>
#include <vector>

#include "filter/grayscale_filter.h"
#include "histogram/luma_histogram.h"

namespace {

jintArray NewHistogramArray(
        JNIEnv* env,
        const std::array<int32_t, xincamera::histogram::kHistogramBucketCount>& histogram) {
    jintArray result = env->NewIntArray(
            static_cast<jsize>(xincamera::histogram::kHistogramBucketCount)
    );
    if (result == nullptr) {
        return nullptr;
    }

    std::vector<jint> jniHistogram(histogram.begin(), histogram.end());
    env->SetIntArrayRegion(
            result,
            0,
            static_cast<jsize>(jniHistogram.size()),
            jniHistogram.data()
    );
    return result;
}

jintArray NewEmptyIntArray(JNIEnv* env) {
    return env->NewIntArray(0);
}

} // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_seanchen_xincamera_nativebridge_NativeMethods_nativePreviewPipelineName(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF("Photon Native Engine Ready");
}

/**
 * JNI 入口层只负责 Java / C++ 数据转换。
 *
 * 真正的直方图算法放在 histogram/luma_histogram.cpp，避免算法代码依赖 JNIEnv、
 * jbyteArray 等 JNI 类型，后续更容易做 native 单测和复用。
 */
extern "C"
JNIEXPORT jintArray JNICALL
Java_com_seanchen_xincamera_nativebridge_NativeMethods_computeLumaHistogram(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray yPlane,
        jint width,
        jint height,
        jint rowStride,
        jint pixelStride) {
    if (yPlane == nullptr || width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) {
        return NewHistogramArray(env, {});
    }

    const jsize planeLength = env->GetArrayLength(yPlane);
    jboolean isCopy = JNI_FALSE;
    jbyte* yData = env->GetByteArrayElements(yPlane, &isCopy);
    if (yData == nullptr) {
        return NewHistogramArray(env, {});
    }

    const auto* yPlaneData = reinterpret_cast<const uint8_t*>(yData);
    const auto histogram = xincamera::histogram::ComputeLumaHistogram(
            yPlaneData,
            width,
            height,
            rowStride,
            pixelStride,
            static_cast<std::size_t>(planeLength)
    );

    env->ReleaseByteArrayElements(yPlane, yData, JNI_ABORT);
    return NewHistogramArray(env, histogram);
}

/**
 * 灰度 JNI 入口。
 *
 * Java 层传入 Bitmap 拆出的 ARGB_8888 IntArray；JNI 层检查尺寸并把数组交给 core 层。
 * core 返回新的像素数组后，再复制成 jintArray 返回给 Kotlin。
 */
extern "C"
JNIEXPORT jintArray JNICALL
Java_com_seanchen_xincamera_nativebridge_NativeMethods_applyGrayscaleArgb8888(
        JNIEnv* env,
        jobject /* this */,
        jintArray pixels,
        jint width,
        jint height) {
    if (pixels == nullptr || width <= 0 || height <= 0) {
        return NewEmptyIntArray(env);
    }

    const jsize pixelCount = env->GetArrayLength(pixels);
    const auto expectedCount = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (expectedCount <= 0 || expectedCount > pixelCount) {
        return NewEmptyIntArray(env);
    }

    jboolean isCopy = JNI_FALSE;
    jint* inputPixels = env->GetIntArrayElements(pixels, &isCopy);
    if (inputPixels == nullptr) {
        return NewEmptyIntArray(env);
    }

    const auto grayscalePixels = xincamera::filter::ApplyGrayscaleArgb8888(
            reinterpret_cast<const int32_t*>(inputPixels),
            static_cast<std::size_t>(expectedCount)
    );

    env->ReleaseIntArrayElements(pixels, inputPixels, JNI_ABORT);

    jintArray output = env->NewIntArray(static_cast<jsize>(grayscalePixels.size()));
    if (output == nullptr) {
        return nullptr;
    }

    std::vector<jint> jniPixels(grayscalePixels.begin(), grayscalePixels.end());
    env->SetIntArrayRegion(
            output,
            0,
            static_cast<jsize>(jniPixels.size()),
            jniPixels.data()
    );
    return output;
}
