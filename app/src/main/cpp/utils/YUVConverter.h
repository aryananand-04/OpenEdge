#ifndef YUVCONVERTER_H
#define YUVCONVERTER_H

#include <opencv2/opencv.hpp>

/**
 * Utility for YUV → BGR conversion.
 * Handles NV21 format conversion using OpenCV.
 */
class YUVConverter {
public:
    static cv::Mat yuvToBgr(const unsigned char* data, int width, int height);
};

#endif

