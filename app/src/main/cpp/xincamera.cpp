#include <jni.h>
#include <algorithm>
#include <array>
#include <cstdint>
#include <vector>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_seanchen_xincamera_nativebridge_NativeBridge_nativePreviewPipelineName(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF("Photon Native Engine Ready");
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_seanchen_xincamera_nativebridge_NativeBridge_computeLumaHistogram(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray yPlane,
        jint width,
        jint height,
        jint rowStride,
        jint pixelStride) {
    std::array<jint, 256> histogram{};
    histogram.fill(0);

    if (yPlane == nullptr || width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0) {
        jintArray emptyResult = env->NewIntArray(256);
        env->SetIntArrayRegion(emptyResult, 0, 256, histogram.data());
        return emptyResult;
    }

    const jsize planeLength = env->GetArrayLength(yPlane);
    jboolean isCopy = JNI_FALSE;
    jbyte* yData = env->GetByteArrayElements(yPlane, &isCopy);

    if (yData != nullptr) {
        for (jint row = 0; row < height; ++row) {
            const jint rowStart = row * rowStride;
            if (rowStart >= planeLength) {
                break;
            }

            for (jint col = 0; col < width; ++col) {
                const jint index = rowStart + col * pixelStride;
                if (index >= planeLength) {
                    break;
                }

                const auto luma = static_cast<unsigned char>(yData[index]);
                histogram[luma] += 1;
            }
        }

        env->ReleaseByteArrayElements(yPlane, yData, JNI_ABORT);
    }

    jintArray result = env->NewIntArray(256);
    env->SetIntArrayRegion(result, 0, 256, histogram.data());
    return result;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_seanchen_xincamera_nativebridge_NativeBridge_applyGrayscaleArgb8888(
        JNIEnv* env,
        jobject /* this */,
        jintArray pixels,
        jint width,
        jint height) {
    if (pixels == nullptr || width <= 0 || height <= 0) {
        return env->NewIntArray(0);
    }

    const jsize pixelCount = env->GetArrayLength(pixels);
    const auto expectedCount = static_cast<int64_t>(width) * static_cast<int64_t>(height);
    if (expectedCount <= 0 || expectedCount > pixelCount) {
        return env->NewIntArray(0);
    }

    jintArray output = env->NewIntArray(pixelCount);
    if (output == nullptr) {
        return nullptr;
    }

    jboolean isCopy = JNI_FALSE;
    jint* inputPixels = env->GetIntArrayElements(pixels, &isCopy);
    if (inputPixels == nullptr) {
        return output;
    }

    std::vector<jint> grayPixels(pixelCount);
    for (jsize index = 0; index < pixelCount; ++index) {
        // Android Bitmap.Config.ARGB_8888 is packed as 0xAARRGGBB in Kotlin IntArray.
        // We cast to uint32_t before shifting so signed jint values do not sign-extend.
        const uint32_t argb = static_cast<uint32_t>(inputPixels[index]);
        const uint32_t alpha = argb & 0xFF000000u;
        const uint32_t red = (argb >> 16u) & 0xFFu;
        const uint32_t green = (argb >> 8u) & 0xFFu;
        const uint32_t blue = argb & 0xFFu;

        // Rec.601 luminance approximation:
        // gray = 0.299R + 0.587G + 0.114B.
        // The integer weights sum to 256, so shifting by 8 replaces a slower division.
        const uint32_t gray = (77u * red + 150u * green + 29u * blue) >> 8u;

        // Keep the original alpha channel and write the same gray value into R/G/B.
        grayPixels[index] = static_cast<jint>(
                alpha | (gray << 16u) | (gray << 8u) | gray
        );
    }

    env->ReleaseIntArrayElements(pixels, inputPixels, JNI_ABORT);
    env->SetIntArrayRegion(output, 0, pixelCount, grayPixels.data());
    return output;
}
