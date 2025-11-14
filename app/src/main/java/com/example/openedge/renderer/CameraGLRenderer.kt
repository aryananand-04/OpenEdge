package com.example.openedge.renderer

import android.opengl.GLSurfaceView
import android.util.Log
import com.example.openedge.NativeBridge
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES renderer for camera frames.
 * Handles initialization and frame rendering callbacks.
 */
class CameraGLRenderer(
    private val onFrameAvailable: (() -> Unit)?
) : GLSurfaceView.Renderer {
    
    private val TAG = "CameraGLRenderer"
    private var width = 0
    private var height = 0
    private var glInitialized = false
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "Surface created")
        glInitialized = false
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: ${width}x${height}")
        this.width = width
        this.height = height
        
        if (!glInitialized) {
            val success = NativeBridge.initGL(width, height)
            if (success) {
                glInitialized = true
                Log.d(TAG, "OpenGL initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize OpenGL")
            }
        } else {
            NativeBridge.setViewport(width, height)
        }
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // Render frame on GL thread
        NativeBridge.renderFrame()
        onFrameAvailable?.invoke()
    }
    
    fun cleanup() {
        if (glInitialized) {
            NativeBridge.cleanupNative()
            glInitialized = false
            Log.d(TAG, "Renderer cleaned up")
        }
    }
}

