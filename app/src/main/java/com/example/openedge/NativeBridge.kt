package com.example.openedge

import android.util.Log

/**
 * Bridges Kotlin ↔ C++.
 * Loads native-lib.so and exposes native functions for:
 * - Frame processing (YUV → BGR → OpenCV → OpenGL)
 * - OpenGL ES rendering
 * - Processing mode control
 */
object NativeBridge {
    private const val TAG = "NativeBridge"
    
    init {
        try {
            System.loadLibrary("native-lib")
            Log.d(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    /**
     * Initialize native components (FrameProcessor, etc.)
     */
    external fun initNative(): Boolean

    /**
     * Initialize OpenGL ES renderer.
     * @param width Viewport width
     * @param height Viewport height
     */
    external fun initGL(width: Int, height: Int): Boolean

    /**
     * Cleanup native resources.
     */
    external fun cleanupNative()

    /**
     * Set processing mode: 0=original, 1=grayscale, 2=blur, 3=edge
     */
    external fun setProcessingMode(mode: Int)

    /**
     * Process frame: YUV → BGR → OpenCV processing → OpenGL rendering.
     * @param data YUV NV21 frame data
     * @param width Frame width
     * @param height Frame height
     */
    external fun processFrame(data: ByteArray, width: Int, height: Int)

    /**
     * Update viewport dimensions.
     */
    external fun setViewport(width: Int, height: Int)

    /**
     * Render frame (called from GL thread).
     */
    external fun renderFrame()
}

