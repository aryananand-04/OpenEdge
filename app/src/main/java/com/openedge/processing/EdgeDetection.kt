package com.openedge.processing

import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import java.nio.ByteBuffer

object EdgeDetection {
    private const val TAG = "EdgeDetection"
    private var nativeInitialized = false

    init {
        try {
            System.loadLibrary("openedge")
            nativeInitialized = nativeInit()
            if (nativeInitialized) {
                Log.d(TAG, "Edge detection native library loaded successfully")
            } else {
                Log.e(TAG, "Failed to initialize edge detection")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading native library", e)
            nativeInitialized = false
        }
    }

    private external fun nativeInit(): Boolean
    private external fun nativeProcessFrame(
        yuvData: ByteArray,
        width: Int,
        height: Int,
        outputBuffer: IntArray
    ): Int

    fun processFrame(image: Image): IntArray? {
        if (!nativeInitialized) {
            Log.e(TAG, "Native library not initialized")
            return null
        }

        if (image.format != ImageFormat.YUV_420_888) {
            Log.e(TAG, "Unsupported image format: ${image.format}")
            return null
        }

        val width = image.width
        val height = image.height
        val planes = image.planes

        // Get Y, U, V planes
        val yBuffer: ByteBuffer = planes[0].buffer
        val uBuffer: ByteBuffer = planes[1].buffer
        val vBuffer: ByteBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // YUV_420_888: Y plane full size, U and V planes quarter size each
        // Convert to NV21 format (Y plane + interleaved VU)
        val nv21Size = width * height * 3 / 2
        val nv21 = ByteArray(nv21Size)

        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)
        yBuffer.rewind()

        // Interleave U and V planes into VU format (NV21)
        val uRowStride = planes[1].rowStride
        val uPixelStride = planes[1].pixelStride
        val vRowStride = planes[2].rowStride
        val vPixelStride = planes[2].pixelStride

        val uvRowCount = height / 2
        val uvSize = width / 2
        var yIndex = width * height

        for (row in 0 until uvRowCount) {
            var uIndex = row * uRowStride
            var vIndex = row * vRowStride
            
            for (col in 0 until uvSize) {
                nv21[yIndex++] = vBuffer[vIndex]
                nv21[yIndex++] = uBuffer[uIndex]
                uIndex += uPixelStride
                vIndex += vPixelStride
            }
        }

        // Create output buffer (RGBA format - 4 bytes per pixel)
        val outputBuffer = IntArray(width * height)

        // Process frame through native code
        val result = nativeProcessFrame(nv21, width, height, outputBuffer)

        if (result != 0) {
            Log.e(TAG, "Native processing failed with code: $result")
            return null
        }

        return outputBuffer
    }

    fun isInitialized(): Boolean = nativeInitialized
}

