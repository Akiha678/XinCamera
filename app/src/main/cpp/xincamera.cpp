#include <jni.h>
#include <algorithm>
#include <array>

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
