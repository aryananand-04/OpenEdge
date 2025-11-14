#include <jni.h>
#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <android/log.h>
#include <cstring>
#include "processor/FrameProcessor.h"
#include "utils/YUVConverter.h"
#include "renderer/GLRenderer.h"

#define LOG_TAG "native-lib"
#define ALOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Global instances
static FrameProcessor* g_frameProcessor = nullptr;
static GLRenderer* g_glRenderer = nullptr;
static int g_processingMode = 0; // 0=original, 1=grayscale, 2=blur, 3=edge

/**
 * Initialize native components.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_openedge_NativeBridge_initNative(JNIEnv* env, jobject thiz) {
    ALOG("initNative called");
    
    if (g_frameProcessor == nullptr) {
        g_frameProcessor = new FrameProcessor();
        ALOG("FrameProcessor created");
    }
    
    return JNI_TRUE;
}

/**
 * Initialize OpenGL renderer.
 */
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_openedge_NativeBridge_initGL(JNIEnv* env, jobject thiz, jint width, jint height) {
    ALOG("initGL called: %dx%d", width, height);
    
    if (g_glRenderer == nullptr) {
        g_glRenderer = new GLRenderer();
    }
    
    bool success = g_glRenderer->init(width, height);
    if (!success) {
        ALOGE("Failed to initialize GL renderer");
        return JNI_FALSE;
    }
    
    return JNI_TRUE;
}

/**
 * Cleanup native resources.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_openedge_NativeBridge_cleanupNative(JNIEnv* env, jobject thiz) {
    ALOG("cleanupNative called");
    
    if (g_glRenderer != nullptr) {
        g_glRenderer->cleanup();
        delete g_glRenderer;
        g_glRenderer = nullptr;
    }
    
    if (g_frameProcessor != nullptr) {
        delete g_frameProcessor;
        g_frameProcessor = nullptr;
    }
}

/**
 * Set processing mode.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_openedge_NativeBridge_setProcessingMode(JNIEnv* env, jobject thiz, jint mode) {
    g_processingMode = mode;
    ALOG("Processing mode set to %d", mode);
}

/**
 * Process frame: YUV → BGR → OpenCV processing → OpenGL rendering.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_openedge_NativeBridge_processFrame(JNIEnv* env, jobject thiz,
                                                     jbyteArray data, jint width, jint height) {
    if (g_frameProcessor == nullptr || g_glRenderer == nullptr) {
        ALOGE("Native components not initialized");
        return;
    }
    
    // Get YUV data from Java
    jbyte* yuvData = env->GetByteArrayElements(data, nullptr);
    if (yuvData == nullptr) {
        ALOGE("Failed to get YUV data");
        return;
    }
    
    // Convert YUV NV21 to BGR
    cv::Mat bgrMat = YUVConverter::yuvToBgr((unsigned char*)yuvData, width, height);
    env->ReleaseByteArrayElements(data, yuvData, JNI_ABORT);
    
    if (bgrMat.empty()) {
        ALOGE("YUV conversion failed");
        return;
    }
    
    // Process frame (grayscale, blur, edge detection, etc.)
    cv::Mat processedMat = g_frameProcessor->processFrame(bgrMat, g_processingMode);
    
    if (processedMat.empty()) {
        ALOGE("Frame processing failed");
        return;
    }
    
    // Update frame for rendering (thread-safe, will be rendered on GL thread)
    g_glRenderer->updateFrame(processedMat);
}

/**
 * Update viewport size.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_openedge_NativeBridge_setViewport(JNIEnv* env, jobject thiz, jint width, jint height) {
    if (g_glRenderer != nullptr) {
        g_glRenderer->setViewport(width, height);
    }
}

/**
 * Render frame (called from GL thread via onDrawFrame).
 */
extern "C" JNIEXPORT void JNICALL
Java_com_example_openedge_NativeBridge_renderFrame(JNIEnv* env, jobject thiz) {
    if (g_glRenderer != nullptr) {
        g_glRenderer->renderFrame();
    }
}

