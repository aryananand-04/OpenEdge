package com.openedge.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager as AndroidCameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class CameraManager(
    private val context: Context,
    private val glSurfaceView: GLSurfaceView
) {

    companion object {
        private const val TAG = "CameraManager"
        private const val MIN_PREVIEW_WIDTH = 640
        private const val MIN_PREVIEW_HEIGHT = 480
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private lateinit var previewSize: Size

    // Threads and Handlers for safe, non-blocking camera operations
    private var cameraThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var imageReaderThread: HandlerThread? = null
    private var imageReaderHandler: Handler? = null

    private lateinit var imageReader: ImageReader
    private var surfaceTexture: SurfaceTexture? = null
    private var cameraTextureId: Int = 0

    // Hold builder and request so session callback can access them
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var captureRequest: CaptureRequest? = null

    // Callbacks to communicate with MainActivity
    private var fpsCallback: ((Float) -> Unit)? = null
    private var processedFrameCallback: ((IntArray, Int, Int) -> Unit)? = null
    private var onCameraReadyCallback: ((Int) -> Unit)? = null

    // State management
    @Volatile
    var edgeDetectionEnabled = false
    private var isCameraSessionActive = false

    // FPS Calculation
    private var frameCount = 0
    private var lastFpsTimestamp = System.currentTimeMillis()

    // --- Public API for MainActivity ---

    fun setFpsCallback(callback: (Float) -> Unit) {
        fpsCallback = callback
    }

    fun setProcessedFrameCallback(callback: (IntArray, Int, Int) -> Unit) {
        processedFrameCallback = callback
    }

    fun setOnCameraReadyCallback(callback: (Int) -> Unit) {
        onCameraReadyCallback = callback
    }

    fun getSurfaceTexture(): SurfaceTexture? {
        return surfaceTexture
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun onResume(width: Int, height: Int) {
        startBackgroundThreads()
        if (hasCameraPermission() && !isCameraSessionActive) {
            openCamera(width, height)
        }
    }

    fun onPause() {
        closeCamera()
        stopBackgroundThreads()
    }

    // --- Camera Lifecycle and State Management ---

    private fun openCamera(width: Int, height: Int) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager
        cameraHandler?.post {
            try {
                val cameraId = getBackFacingCameraId(manager)
                if (cameraId == null) {
                    Log.e(TAG, "No back-facing camera found.")
                    return@post
                }

                val characteristics = manager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?: throw RuntimeException("Cannot get stream configuration map.")

                previewSize = chooseOptimalPreviewSize(map.getOutputSizes(SurfaceTexture::class.java), width, height)

                if (hasCameraPermission()) {
                    manager.openCamera(cameraId, cameraStateCallback, cameraHandler)
                }
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Cannot access the camera.", e)
            } catch (e: SecurityException) {
                Log.e(TAG, "Camera permission not granted.", e)
            }
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera device opened.")
            cameraDevice = camera
            isCameraSessionActive = true
            startPreview()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "Camera device disconnected.")
            camera.close()
            isCameraSessionActive = false
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera device error: $error")
            camera.close()
            isCameraSessionActive = false
        }
    }

    private fun startPreview() {
        val camera = cameraDevice ?: return

        // Wait until GL thread creates surfaceTexture
        val ready = setupSurfaceTexture()
        if (!ready) {
            Log.e(TAG, "SurfaceTexture not ready — aborting startPreview() to avoid crash.")
            return
        }


        surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
        val surface = Surface(surfaceTexture)

        setupImageReader()
        val imageReaderSurface = imageReader.surface

        cameraHandler?.post {
            try {
                // store builder & request at class level so the session callback can access it
                captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(surface)
                    addTarget(imageReaderSurface)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                }

                captureRequest = captureRequestBuilder?.build()

                camera.createCaptureSession(listOf(surface, imageReaderSurface), sessionStateCallback, cameraHandler)
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to start camera session.", e)
            }
        }
    }

    private val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "Capture session configured.")
            captureSession = session
            try {
                // use the built request that we stored earlier
                val request = captureRequest
                if (request != null) {
                    session.setRepeatingRequest(request, null, cameraHandler)
                } else {
                    Log.e(TAG, "CaptureRequest is null; cannot start repeating request.")
                }
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to set repeating request.", e)
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "Failed to configure capture session.")
        }
    }

    private fun closeCamera() {
        if (isCameraSessionActive) {
            isCameraSessionActive = false
            cameraHandler?.post {
                try {
                    captureSession?.close()
                    captureSession = null
                    cameraDevice?.close()
                    cameraDevice = null
                    // imageReader is lateinit — guard before closing
                    if (::imageReader.isInitialized) {
                        imageReader.close()
                    }
                    // release SurfaceTexture if present
                    surfaceTexture?.release()
                    surfaceTexture = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing camera resources.", e)
                }
            }
        } else {
            // still try to release resources if they exist
            try {
                captureSession?.close()
                captureSession = null
                cameraDevice?.close()
                cameraDevice = null
                if (::imageReader.isInitialized) {
                    imageReader.close()
                }
                surfaceTexture?.release()
                surfaceTexture = null
            } catch (e: Exception) {
                Log.e(TAG, "Error closing camera resources.", e)
            }
        }
    }

    // --- Background Thread Management ---

    private fun startBackgroundThreads() {
        cameraThread = HandlerThread("CameraThread").also { it.start() }
        cameraHandler = Handler(cameraThread!!.looper)
        imageReaderThread = HandlerThread("ImageReaderThread").also { it.start() }
        imageReaderHandler = Handler(imageReaderThread!!.looper)
    }

    private fun stopBackgroundThreads() {
        cameraThread?.quitSafely()
        imageReaderThread?.quitSafely()
        try {
            cameraThread?.join(500) // Wait max 500ms for thread to die
            cameraThread = null
            cameraHandler = null
            imageReaderThread?.join(500)
            imageReaderThread = null
            imageReaderHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping threads.", e)
        }
    }

    // --- Surface and ImageReader Setup ---

    private fun setupSurfaceTexture(): Boolean {
        // If already created, return quickly
        if (surfaceTexture != null) return true

        val latch = CountDownLatch(1)
        val textures = IntArray(1)

        glSurfaceView.queueEvent {
            try {
                GLES20.glGenTextures(1, textures, 0)
                cameraTextureId = textures[0]
                surfaceTexture = SurfaceTexture(cameraTextureId).also {
                    it.setOnFrameAvailableListener {
                        glSurfaceView.requestRender()
                    }
                }
                onCameraReadyCallback?.invoke(cameraTextureId)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to create SurfaceTexture on GL thread", t)
            } finally {
                latch.countDown()
            }
        }

        return try {
            val ok = latch.await(500, TimeUnit.MILLISECONDS)
            if (!ok) {
                Log.e(TAG, "Timeout waiting for SurfaceTexture creation on GL thread")
            }
            ok && surfaceTexture != null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for SurfaceTexture", e)
            Thread.currentThread().interrupt()
            false
        }
    }


    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener

            calculateFps()

            if (edgeDetectionEnabled) {
                val pixels = convertYUVToGrayscale(image)
                processedFrameCallback?.invoke(pixels, image.width, image.height)
            }

            image.close()
        }, imageReaderHandler)
    }

    // --- Utility and Processing Methods ---

    private fun calculateFps() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFpsTimestamp >= 1000) {
            fpsCallback?.invoke(frameCount.toFloat())
            frameCount = 0
            lastFpsTimestamp = currentTime
        }
    }

    private fun getBackFacingCameraId(manager: AndroidCameraManager): String? {
        for (cameraId in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) {
                return cameraId
            }
        }
        return null
    }

    private fun chooseOptimalPreviewSize(choices: Array<Size>, viewWidth: Int, viewHeight: Int): Size {
        val bigEnough = mutableListOf<Size>()
        for (option in choices) {
            if (option.width >= MIN_PREVIEW_WIDTH && option.height >= MIN_PREVIEW_HEIGHT) {
                bigEnough.add(option)
            }
        }
        return when {
            bigEnough.isNotEmpty() -> Collections.min(bigEnough, CompareSizesByArea())
            choices.isNotEmpty() -> choices[0] // Fallback
            else -> Size(MIN_PREVIEW_WIDTH, MIN_PREVIEW_HEIGHT)
        }
    }

    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // Correctly compare the area of two sizes
            return (lhs.width.toLong() * lhs.height).compareTo(rhs.width.toLong() * rhs.height)
        }
    }

    private fun convertYUVToGrayscale(image: android.media.Image): IntArray {
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        // make sure buffer position is at 0 before reading indexed bytes
        yBuffer.rewind()
        val pixels = IntArray(image.width * image.height)
        for (i in pixels.indices) {
            val y = yBuffer.get(i).toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (y shl 16) or (y shl 8) or y
        }
        return pixels
    }
}
