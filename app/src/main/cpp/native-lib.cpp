#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "OpenEdgeNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_openedge_MainActivity_stringFromJNI(JNIEnv* env, jobject) {
    std::string hello = "Hello from C++ NDK!";
    LOGD("Native library loaded successfully");
    return env->NewStringUTF(hello.c_str());
}
