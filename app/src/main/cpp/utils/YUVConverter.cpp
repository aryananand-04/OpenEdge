#include "YUVConverter.h"
#include <opencv2/imgproc.hpp>
#include <android/log.h>

#define LOG_TAG "YUVConverter"
#define ALOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

/**
 * Convert YUV NV21 raw frame to a BGR cv::Mat.
 * 
 * NV21 format: Y plane followed by interleaved VU plane
 * - Y plane: width * height bytes
 * - VU plane: (width * height) / 2 bytes (interleaved V, U)
 * 
 * Uses OpenCV's native NV21 conversion for efficiency.
 */
cv::Mat YUVConverter::yuvToBgr(const unsigned char* data, int width, int height) {
    if (data == nullptr || width <= 0 || height <= 0) {
        ALOG("Invalid input parameters: data=%p, width=%d, height=%d", data, width, height);
        return cv::Mat();
    }

    // Create Mat from NV21 data (Y plane + interleaved VU)
    // Total size: width * height * 3 / 2
    int totalSize = width * height * 3 / 2;
    cv::Mat nv21Mat(height + height / 2, width, CV_8UC1, (void*)data);
    
    // Convert NV21 to BGR using OpenCV
    cv::Mat bgrMat;
    cv::cvtColor(nv21Mat, bgrMat, cv::COLOR_YUV2BGR_NV21);
    
    return bgrMat;
}

