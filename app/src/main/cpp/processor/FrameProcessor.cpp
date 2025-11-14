#include "FrameProcessor.h"
#include <opencv2/imgproc.hpp>
#include <android/log.h>

#define LOG_TAG "FrameProcessor"
#define ALOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

FrameProcessor::FrameProcessor() : currentMode(0) {
    ALOG("FrameProcessor initialized");
}

FrameProcessor::~FrameProcessor() {
    ALOG("FrameProcessor destroyed");
}

/**
 * Convert an image to grayscale.
 */
cv::Mat FrameProcessor::toGray(const cv::Mat& input) {
    if (input.empty()) {
        ALOG("toGray: input is empty");
        return cv::Mat();
    }
    
    cv::Mat gray;
    cv::cvtColor(input, gray, cv::COLOR_BGR2GRAY);
    
    // Convert back to BGR for display (3 channels)
    cv::Mat grayBgr;
    cv::cvtColor(gray, grayBgr, cv::COLOR_GRAY2BGR);
    
    return grayBgr;
}

/**
 * Apply Gaussian blur to the image.
 */
cv::Mat FrameProcessor::applyBlur(const cv::Mat& input, int kernelSize) {
    if (input.empty()) {
        ALOG("applyBlur: input is empty");
        return cv::Mat();
    }
    
    // Ensure kernel size is odd
    if (kernelSize % 2 == 0) {
        kernelSize++;
    }
    
    cv::Mat blurred;
    cv::GaussianBlur(input, blurred, cv::Size(kernelSize, kernelSize), 0);
    
    return blurred;
}

/**
 * Apply Canny edge detection.
 */
cv::Mat FrameProcessor::detectEdges(const cv::Mat& input, double lowThreshold, double highThreshold) {
    if (input.empty()) {
        ALOG("detectEdges: input is empty");
        return cv::Mat();
    }
    
    cv::Mat gray, edges;
    
    // Convert to grayscale first
    cv::cvtColor(input, gray, cv::COLOR_BGR2GRAY);
    
    // Apply Canny edge detection
    cv::Canny(gray, edges, lowThreshold, highThreshold);
    
    // Convert back to BGR for display
    cv::Mat edgesBgr;
    cv::cvtColor(edges, edgesBgr, cv::COLOR_GRAY2BGR);
    
    return edgesBgr;
}

/**
 * Process frame with current mode.
 */
cv::Mat FrameProcessor::processFrame(const cv::Mat& input, int mode) {
    if (input.empty()) {
        ALOG("processFrame: input is empty");
        return cv::Mat();
    }
    
    currentMode = mode;
    
    switch (mode) {
        case 0: // Original
            return input.clone();
        case 1: // Grayscale
            return toGray(input);
        case 2: // Blur
            return applyBlur(input, 9);
        case 3: // Edge detection
            return detectEdges(input, 50.0, 150.0);
        default:
            ALOG("Unknown mode %d, returning original", mode);
            return input.clone();
    }
}

