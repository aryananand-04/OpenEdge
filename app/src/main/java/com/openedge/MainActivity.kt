package com.openedge

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.openedge.camera.CameraManager
import com.openedge.gl.CameraRenderer

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100

        init {
            System.loadLibrary("openedge")
        }
    }

    external fun stringFromJNI(): String

    private lateinit var fpsText: TextView
    private lateinit var processingTimeText: TextView
    private lateinit var toggleButton: Button
    private lateinit var glSurfaceView: GLSurfaceView
    private var cameraManager: CameraManager? = null
    private var cameraRenderer: CameraRenderer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Toast.makeText(this, stringFromJNI(), Toast.LENGTH_SHORT).show()

        fpsText = findViewById(R.id.fpsText)
        processingTimeText = findViewById(R.id.processingTimeText)
        toggleButton = findViewById(R.id.toggleButton)
        glSurfaceView = findViewById(R.id.glSurfaceView)

        // Configure GLSurfaceView
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.preserveEGLContextOnPause = true

        // Set up renderer
        cameraRenderer = CameraRenderer()
        glSurfaceView.setRenderer(cameraRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        toggleButton.setOnClickListener {
            Toast.makeText(this, "Toggle clicked!", Toast.LENGTH_SHORT).show()
        }

        // Initialize CameraManager and connect to renderer
        cameraManager = CameraManager(this, glSurfaceView)
        cameraManager?.setOnCameraReadyCallback { textureId ->
            val surfaceTexture = cameraManager?.getSurfaceTexture() ?: return@setOnCameraReadyCallback
            cameraRenderer?.setSurfaceTexture(surfaceTexture, textureId)
            // Set orientation info after camera is ready
            cameraRenderer?.setOrientationInfo(
                cameraManager?.getCameraSensorOrientation() ?: 0,
                cameraManager?.getDisplayRotation() ?: 0,
                cameraManager?.getCameraFacing()
            )
        }
        
        checkCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        if (cameraManager?.hasCameraPermission() == true) {
            glSurfaceView.onResume()
            // Use view dimensions, but queue after layout
            glSurfaceView.post {
                cameraManager?.onResume(glSurfaceView.width, glSurfaceView.height)
                // Update orientation when camera resumes
                cameraRenderer?.setOrientationInfo(
                    cameraManager?.getCameraSensorOrientation() ?: 0,
                    cameraManager?.getDisplayRotation() ?: 0,
                    cameraManager?.getCameraFacing()
                )
            }
        }
    }
    
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Update orientation when device rotates
        glSurfaceView.post {
            cameraRenderer?.setOrientationInfo(
                cameraManager?.getCameraSensorOrientation() ?: 0,
                cameraManager?.getDisplayRotation() ?: 0,
                cameraManager?.getCameraFacing()
            )
            glSurfaceView.requestRender()
        }
    }

    override fun onPause() {
        super.onPause()
        cameraManager?.onPause()
        glSurfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager?.closeCamera()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
                // Start camera after permission is granted
                glSurfaceView.post {
                    cameraManager?.onResume(glSurfaceView.width, glSurfaceView.height)
                }
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}