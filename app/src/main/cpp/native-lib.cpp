#include <jni.h>
#include <opencv2/opencv.hpp>
#include <cstring>
#include <android/log.h>

static int gMode = 0;

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_edgeviewer_MainActivity_testOpenCV(JNIEnv* env, jobject) {
    cv::Mat m = cv::Mat::eye(3, 3, CV_8UC1);
    return m.rows;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_edgeviewer_MainActivity_nativeHello(JNIEnv* env, jobject) {
    return env->NewStringUTF("EdgeViewer JNI ready");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_edgeviewer_MainActivity_setViewerMode(JNIEnv*, jobject, jint mode) {
    gMode = (mode == 1) ? 1 : 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_edgeviewer_MainActivity_processFrame(
        JNIEnv* env,
        jobject,
        jbyteArray inputArray,
        jint width,
        jint height,
        jbyteArray outputArray) {

    __android_log_print(ANDROID_LOG_DEBUG, "EdgeViewer", "processFrame called w=%d h=%d mode=%d", width, height, gMode);

    jbyte* input = env->GetByteArrayElements(inputArray, nullptr);
    jbyte* output = env->GetByteArrayElements(outputArray, nullptr);

    // Validate input length to catch packing issues early
    jsize inLen = env->GetArrayLength(inputArray);
    jsize expected = width * height + (width * height) / 2;
    if (inLen < expected) {
        __android_log_print(ANDROID_LOG_WARN, "EdgeViewer", "inputArray length %d < expected NV21 size %d", inLen, expected);
    }

    cv::Mat yuv(height + height / 2, width, CV_8UC1, (unsigned char*)input);
    cv::Mat bgr;
    cv::cvtColor(yuv, bgr, cv::COLOR_YUV2BGR_NV21);

    cv::Mat finalOut;

    if (gMode == 0) {
        cv::Mat edges;
        cv::Canny(bgr, edges, 100, 200);
        cv::resize(edges, finalOut, cv::Size(width, height), 0, 0, cv::INTER_NEAREST);
    } else {
        cv::Mat gray;
        cv::cvtColor(bgr, gray, cv::COLOR_BGR2GRAY);
        cv::resize(gray, finalOut, cv::Size(width, height), 0, 0, cv::INTER_NEAREST);
    }

    // Ensure continuous single-channel output before copying
    cv::Mat outCont;
    if (finalOut.isContinuous() && finalOut.type() == CV_8UC1) {
        outCont = finalOut;
    } else {
        finalOut.convertTo(outCont, CV_8UC1);
        if (!outCont.isContinuous()) outCont = outCont.clone();
    }

    size_t copyBytes = (size_t)width * (size_t)height;

    // If the processed image is nearly empty (few non-zero pixels), fall back to
    // copying the raw Y plane from the NV21 input. This helps diagnose whether
    // the processing stage or the input packing is at fault for horizontal bands.
    int total = width * height;
    int nonZero = 0;
    const unsigned char* d = outCont.data;
    for (int i = 0; i < total; ++i) {
        if (d[i] != 0) nonZero++;
    }
    float nonZeroPct = (total>0)?(100.0f * nonZero / total):0.0f;
    if (nonZeroPct < 2.0f) {
        __android_log_print(ANDROID_LOG_WARN, "EdgeViewer", "processed image almost empty (%.2f%%); falling back to copying Y plane", nonZeroPct);
        // Input is NV21: Y plane is first width*height bytes
        memcpy(output, input, copyBytes);
    } else {
        memcpy(output, outCont.data, copyBytes);
    }

    // Lightweight diagnostics: log min/max and non-zero ratio every 30 frames
    static int frameCounter = 0;
    frameCounter++;
    if ((frameCounter % 10) == 0) {
        int total = width * height;
        int nonZero = 0;
        int minV = 255, maxV = 0;
        const unsigned char* d = finalOut.data;
        for (int i = 0; i < total; ++i) {
            int v = d[i];
            if (v != 0) nonZero++;
            if (v < minV) minV = v;
            if (v > maxV) maxV = v;
        }
        float ratio = (total>0)?(100.0f * nonZero / total):0.0f;
        __android_log_print(ANDROID_LOG_INFO, "EdgeViewer", "processFrame stats: min=%d max=%d nonZero=%.1f%%", minV, maxV, ratio);
    }

    env->ReleaseByteArrayElements(outputArray, output, 0);
    env->ReleaseByteArrayElements(inputArray, input, JNI_ABORT);
}
