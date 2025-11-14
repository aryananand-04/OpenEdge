package com.example.openedge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.openedge.renderer.CameraGLRenderer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    
    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var glRenderer: CameraGLRenderer
    
    private var currentProcessingMode = 0 // 0=original, 1=grayscale, 2=blur, 3=edge
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                initializeNative()
                startCamera()
            } else {
                Log.e(TAG, "Camera permission denied")
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupUI()
        
        if (allPermissionsGranted()) {
            initializeNative()
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    
    private fun setupUI() {
        glSurfaceView = findViewById(R.id.glSurfaceView)
        
        // Setup GLSurfaceView
        glRenderer = CameraGLRenderer(null)
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.setRenderer(glRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        
        // Mode buttons
        findViewById<Button>(R.id.btnOriginal).setOnClickListener {
            setProcessingMode(0)
        }
        findViewById<Button>(R.id.btnGrayscale).setOnClickListener {
            setProcessingMode(1)
        }
        findViewById<Button>(R.id.btnBlur).setOnClickListener {
            setProcessingMode(2)
        }
        findViewById<Button>(R.id.btnEdge).setOnClickListener {
            setProcessingMode(3)
        }
        
        // WebView button
        findViewById<Button>(R.id.btnWebView).setOnClickListener {
            val intent = Intent(this, WebViewActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun initializeNative() {
        val success = NativeBridge.initNative()
        if (success) {
            Log.d(TAG, "Native components initialized")
        } else {
            Log.e(TAG, "Failed to initialize native components")
        }
    }
    
    private fun setProcessingMode(mode: Int) {
        currentProcessingMode = mode
        NativeBridge.setProcessingMode(mode)
        val modeNames = arrayOf("Original", "Grayscale", "Blur", "Edge Detection")
        Toast.makeText(this, "Mode: ${modeNames[mode]}", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Processing mode set to: ${modeNames[mode]}")
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        
        // ImageAnalysis for processing frames
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .apply {
                setAnalyzer(cameraExecutor) { image ->
                    processImage(image)
                }
            }

        val selector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, selector, imageAnalysis)
            Log.d(TAG, "Camera bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Camera bind failed", e)
        }
    }

    private fun processImage(image: ImageProxy) {
        try {
            val width = image.width
            val height = image.height
            
            // YUV_420_888 format: planar Y, U, V planes
            // Convert to NV21: Y plane + interleaved VU plane
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]
            
            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer
            
            val ySize = width * height
            val uvSize = ySize / 4 // U and V are each 1/4 of Y size
            val nv21Size = ySize + uvSize * 2
            
            val nv21 = ByteArray(nv21Size)
            
            // Copy Y plane (accounting for row stride)
            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val yBufferArray = ByteArray(yBuffer.remaining())
            yBuffer.get(yBufferArray)
            
            var yOffset = 0
            for (i in 0 until height) {
                System.arraycopy(yBufferArray, i * yRowStride, nv21, yOffset, width)
                yOffset += width
            }
            
            // Get U and V data
            val uBufferArray = ByteArray(uBuffer.remaining())
            val vBufferArray = ByteArray(vBuffer.remaining())
            uBuffer.get(uBufferArray)
            vBuffer.get(vBufferArray)
            
            val uRowStride = uPlane.rowStride
            val vRowStride = vPlane.rowStride
            val uvWidth = width / 2
            val uvHeight = height / 2
            
            // Interleave V and U into VU format (NV21)
            var uvOffset = ySize
            for (i in 0 until uvHeight) {
                for (j in 0 until uvWidth) {
                    val uIndex = i * uRowStride + j * uPlane.pixelStride
                    val vIndex = i * vRowStride + j * vPlane.pixelStride
                    
                    nv21[uvOffset++] = vBufferArray[vIndex] // V first
                    nv21[uvOffset++] = uBufferArray[uIndex] // U second
                }
            }
            
            // Process frame in native code (stores frame, will be rendered on GL thread)
            NativeBridge.processFrame(nv21, width, height)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
        } finally {
            image.close()
        }
    }
    
    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
    }
    
    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        glRenderer.cleanup()
        NativeBridge.cleanupNative()
        Log.d(TAG, "MainActivity destroyed")
    }
}

