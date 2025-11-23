#include <jni.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/core.hpp>
#include <opencv2/imgcodecs.hpp>
#include <vector>

#define LOG_TAG "EdgeDetection"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cv;

extern "C" {

/**
 * Process camera frame with Canny edge detection
 * Input: YUV_420_888 format (NV21 or NV12)
 * Output: Grayscale edge image (black background, white edges)
 */
JNIEXPORT jint JNICALL
Java_com_openedge_processing_EdgeDetection_nativeProcessFrame(
        JNIEnv *env,
        jobject thiz,
        jbyteArray yuvData,
        jint width,
        jint height,
        jintArray outputBuffer
) {
    try {
        // Get input data
        jbyte* yuvBytes = env->GetByteArrayElements(yuvData, nullptr);
        jint* outPixels = env->GetIntArrayElements(outputBuffer, nullptr);
        
        if (!yuvBytes || !outPixels) {
            LOGE("Failed to get array elements");
            if (yuvBytes) env->ReleaseByteArrayElements(yuvData, yuvBytes, JNI_ABORT);
            if (outPixels) env->ReleaseIntArrayElements(outputBuffer, outPixels, JNI_ABORT);
            return -1;
        }
        
        // Convert YUV to Mat (assuming NV21 format)
        Mat yuvMat(height + height/2, width, CV_8UC1, (unsigned char*)yuvBytes);
        Mat bgrMat;
        cvtColor(yuvMat, bgrMat, COLOR_YUV2BGR_NV21);
        
        // Convert to grayscale
        Mat grayMat;
        cvtColor(bgrMat, grayMat, COLOR_BGR2GRAY);
        
        // Apply Gaussian blur to reduce noise
        Mat blurredMat;
        GaussianBlur(grayMat, blurredMat, Size(5, 5), 1.5);
        
        // Canny edge detection with thresholds 50 and 150
        Mat edgesMat;
        Canny(blurredMat, edgesMat, 50, 150);
        
        // Convert edges to RGBA format (black background, white edges)
        // White edges = 0xFFFFFFFF, Black background = 0xFF000000
        Mat rgbaMat;
        cvtColor(edgesMat, rgbaMat, COLOR_GRAY2RGBA);
        
        // Copy result to output buffer (RGBA format)
        int* rgbaData = (int*)rgbaMat.data;
        int pixelCount = width * height;
        for (int i = 0; i < pixelCount; i++) {
            // Invert: edges become white (0xFFFFFFFF), background black (0xFF000000)
            int pixel = rgbaData[i];
            if (pixel > 0) {
                // Edge pixel - make white
                outPixels[i] = 0xFFFFFFFF;
            } else {
                // Background - make black
                outPixels[i] = 0xFF000000;
            }
        }
        
        // Release resources
        env->ReleaseByteArrayElements(yuvData, yuvBytes, JNI_ABORT);
        env->ReleaseIntArrayElements(outputBuffer, outPixels, 0);
        
        return 0;
    } catch (const cv::Exception& e) {
        LOGE("OpenCV exception: %s", e.what());
        return -1;
    } catch (...) {
        LOGE("Unknown exception in edge detection");
        return -1;
    }
}

/**
 * Initialize OpenCV
 */
JNIEXPORT jboolean JNICALL
Java_com_openedge_processing_EdgeDetection_nativeInit(JNIEnv *env, jobject thiz) {
    try {
        LOGD("OpenCV initialized - version: %s", CV_VERSION);
        return JNI_TRUE;
    } catch (...) {
        LOGE("Failed to initialize OpenCV");
        return JNI_FALSE;
    }
}

} // extern "C"

