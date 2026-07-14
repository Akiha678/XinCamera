#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_seanchen_xincamera_image_NativeBridge_nativePreviewPipelineName(
        JNIEnv* env,
        jobject /* this */) {
    return env->NewStringUTF("Photon Native Engine Ready");
}
