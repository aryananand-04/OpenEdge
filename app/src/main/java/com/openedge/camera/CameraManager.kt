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
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.openedge.processing.EdgeDetection
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraManager(private val context: Context, private val glSurfaceView: GLSurfaceView) {

    companion object {
        private const val TAG = "CameraManager"
        private const val MAX_PREVIEW_WIDTH = 1920
        private const val MAX_PREVIEW_HEIGHT = 1080
    }

    private var cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSize: Size? = null
    private var cameraId: String? = null
    private var cameraSensorOrientation: Int = 0

    private val cameraOpenCloseLock = Semaphore(1)
    private var surfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var imageReader: ImageReader? = null

    // FPS tracking
    private val frameTimestamps = ArrayDeque<Long>(60)
    private var lastFpsUpdateTime = System.nanoTime()
    private var fpsCallback: ((Double) -> Unit)? = null

    // Edge detection state
    @Volatile
    var edgeDetectionEnabled = false
    private var processedFrameCallback: ((IntArray, Int, Int) -> Unit)? = null

    fun interface OnCameraReadyCallback {
        fun onCameraReady(textureId: Int)
    }

    private var cameraReadyCallback: OnCameraReadyCallback? = null

    fun setOnCameraReadyCallback(callback: OnCameraReadyCallback) {
        cameraReadyCallback = callback
    }

    fun setFpsCallback(callback: (Double) -> Unit) {
        fpsCallback = callback
    }

    fun setProcessedFrameCallback(callback: (IntArray, Int, Int) -> Unit) {
        processedFrameCallback = callback
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice = camera
            Log.d(TAG, "Camera opened: ${camera.id}")
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
            Log.d(TAG, "Camera disconnected")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
            Log.e(TAG, "Camera error: $error")
        }
    }

    private val captureStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            if (cameraDevice == null) return

            captureSession = session
            try {
                val captureRequestBuilder = cameraDevice!!.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW
                )
                previewSurface?.let { captureRequestBuilder.addTarget(it) }
                imageReader?.surface?.let { captureRequestBuilder.addTarget(it) }
                
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON)

                val captureRequest = captureRequestBuilder.build()
                session.setRepeatingRequest(captureRequest, null, null)
                Log.d(TAG, "Camera preview started")
            } catch (e: CameraAccessException) {
                Log.e(TAG, "Failed to start camera preview", e)
            }
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "Failed to configure camera session")
        }

        override fun onClosed(session: CameraCaptureSession) {
            captureSession = null
            Log.d(TAG, "Camera session closed")
        }
    }

    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        image?.let {
            val currentTime = System.nanoTime()
            
            // Track FPS from actual frame arrivals
            frameTimestamps.addLast(currentTime)
            val twoSecondsAgo = currentTime - 2_000_000_000L
            while (frameTimestamps.isNotEmpty() && frameTimestamps.first() < twoSecondsAgo) {
                frameTimestamps.removeFirst()
            }
            
            // Update FPS every 1 second
            if (currentTime - lastFpsUpdateTime >= 1_000_000_000L) {
                if (frameTimestamps.size >= 2) {
                    val timeSpan = frameTimestamps.last() - frameTimestamps.first()
                    if (timeSpan > 0) {
                        val fps = (frameTimestamps.size - 1) * 1_000_000_000.0 / timeSpan
                        fpsCallback?.invoke(fps)
                    }
                } else if (frameTimestamps.size == 1) {
                    val elapsed = currentTime - frameTimestamps.first()
                    if (elapsed > 0) {
                        val fps = 1_000_000_000.0 / elapsed
                        fpsCallback?.invoke(fps)
                    }
                } else {
                    fpsCallback?.invoke(0.0)
                }
                lastFpsUpdateTime = currentTime
            }
            
            // Process frame for edge detection if enabled
            if (edgeDetectionEnabled && processedFrameCallback != null) {
                val processed = EdgeDetection.processFrame(it)
                processed?.let { frame ->
                    processedFrameCallback?.invoke(frame, it.width, it.height)
                }
            }
            
            it.close()
        }
    }

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openCamera(width: Int, height: Int) {
        if (!hasCameraPermission()) {
            Log.e(TAG, "Camera permission not granted")
            return
        }

        try {
            cameraId = getBackCameraId()
            if (cameraId == null) {
                Log.e(TAG, "No back camera found")
                return
            }

            val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
            cameraSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return

            previewSize = chooseOptimalSize(
                map.getOutputSizes(SurfaceTexture::class.java),
                width, height, MAX_PREVIEW_WIDTH, MAX_PREVIEW_HEIGHT
            )

            Log.d(TAG, "Selected preview size: $previewSize")

            // Create ImageReader for frame capture
            imageReader = ImageReader.newInstance(
                previewSize!!.width,
                previewSize!!.height,
                ImageFormat.YUV_420_888,
                2
            )
            imageReader?.setOnImageAvailableListener(imageAvailableListener, null)

            // Queue texture creation on GL thread
            glSurfaceView.queueEvent {
                val textures = IntArray(1)
                GLES20.glGenTextures(1, textures, 0)
                val textureId = textures[0]

                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE
                )

                surfaceTexture = SurfaceTexture(textureId)
                surfaceTexture?.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
                surfaceTexture?.setOnFrameAvailableListener {
                    glSurfaceView.requestRender()
                }
                previewSurface = Surface(surfaceTexture)

                cameraReadyCallback?.onCameraReady(textureId)
                glSurfaceView.requestRender()

                glSurfaceView.post {
                    openCameraInternal()
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to access camera", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
        }
    }

    private fun openCameraInternal() {
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening")
            }

            cameraManager.openCamera(cameraId!!, stateCallback, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
            cameraOpenCloseLock.release()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception opening camera", e)
            cameraOpenCloseLock.release()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening", e)
        }
    }

    private fun createCameraPreviewSession() {
        try {
            previewSurface ?: return

            val surfaces = mutableListOf<Surface>()
            previewSurface?.let { surfaces.add(it) }
            imageReader?.surface?.let { surfaces.add(it) }

            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )
            previewSurface?.let { captureRequestBuilder.addTarget(it) }
            imageReader?.surface?.let { captureRequestBuilder.addTarget(it) }

            cameraDevice!!.createCaptureSession(
                surfaces,
                captureStateCallback,
                null
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to create camera session", e)
        }
    }

    fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            
            captureSession?.close()
            captureSession = null

            cameraDevice?.close()
            cameraDevice = null

            surfaceTexture?.release()
            surfaceTexture = null
            previewSurface?.release()
            previewSurface = null

            imageReader?.close()
            imageReader = null

            cameraOpenCloseLock.release()
            Log.d(TAG, "Camera closed")
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while closing camera", e)
        }
    }

    fun onResume(width: Int, height: Int) {
        if (hasCameraPermission() && cameraDevice == null) {
            openCamera(width, height)
        }
    }

    fun onPause() {
        closeCamera()
    }

    private fun getBackCameraId(): String? {
        return try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id
                }
            }
            null
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to get camera list", e)
            null
        }
    }

    private fun chooseOptimalSize(
        choices: Array<Size>,
        textureViewWidth: Int,
        textureViewHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Size {
        val bigEnough = mutableListOf<Size>()
        val notBigEnough = mutableListOf<Size>()

        val w = textureViewWidth.coerceAtMost(maxWidth)
        val h = textureViewHeight.coerceAtMost(maxHeight)

        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight &&
                option.height == option.width * h / w
            ) {
                if (option.width >= w && option.height >= h) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        return when {
            bigEnough.isNotEmpty() -> {
                bigEnough.minByOrNull { it.width * it.height } ?: choices[0]
            }
            notBigEnough.isNotEmpty() -> {
                notBigEnough.maxByOrNull { it.width * it.height } ?: choices[0]
            }
            else -> {
                choices[0]
            }
        }
    }

    fun getSurfaceTexture(): SurfaceTexture? = surfaceTexture
    fun getPreviewSize(): Size? = previewSize
    fun getDisplayRotation(): Int {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        return when (display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }
}
