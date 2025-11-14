#ifndef FRAMEPROCESSOR_H
#define FRAMEPROCESSOR_H

#include <opencv2/opencv.hpp>

/**
 * FrameProcessor handles various image processing operations.
 * Supports grayscale conversion, blur, and edge detection.
 */
class FrameProcessor {
public:
    FrameProcessor();
    ~FrameProcessor();
    
    /**
     * Convert BGR image to grayscale.
     */
    cv::Mat toGray(const cv::Mat& input);
    
    /**
     * Apply Gaussian blur to the image.
     * @param input Input BGR image
     * @param kernelSize Blur kernel size (must be odd)
     */
    cv::Mat applyBlur(const cv::Mat& input, int kernelSize = 5);
    
    /**
     * Apply Canny edge detection.
     * @param input Input BGR image
     * @param lowThreshold Lower threshold for edge detection
     * @param highThreshold Upper threshold for edge detection
     */
    cv::Mat detectEdges(const cv::Mat& input, double lowThreshold = 50.0, double highThreshold = 150.0);
    
    /**
     * Process frame with current mode.
     * @param input Input BGR image
     * @param mode Processing mode: 0=original, 1=grayscale, 2=blur, 3=edge
     */
    cv::Mat processFrame(const cv::Mat& input, int mode);
    
private:
    int currentMode;
};

#endif

