package com.openedge

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.openedge.camera.CameraManager
import com.openedge.gl.CameraRenderer
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
    }

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var fpsText: TextView
    private lateinit var processingTimeText: TextView
    private lateinit var modeText: TextView
    private lateinit var titleText: TextView

    private lateinit var btnRaw: Button
    private lateinit var btnGrayscale: Button
    private lateinit var btnEdges: Button
    private lateinit var btnCapture: ImageButton
    private lateinit var toggleButton: Button

    private lateinit var sensitivityPanel: LinearLayout
    private lateinit var sensitivitySlider: SeekBar

    private var cameraManager: CameraManager? = null
    private var cameraRenderer: CameraRenderer? = null

    private var currentMode = 0

    private lateinit var vibrator: Vibrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Init vibrator in a compatibility-safe way
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Typed API available from M
            getSystemService(Vibrator::class.java) ?: throw IllegalStateException("No vibrator available")
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // Find all views
        glSurfaceView = findViewById(R.id.glSurfaceView)
        fpsText = findViewById(R.id.fpsText)
        processingTimeText = findViewById(R.id.processingTimeText)
        modeText = findViewById(R.id.modeText)
        titleText = findViewById(R.id.titleText)
        btnRaw = findViewById(R.id.btnRaw)
        btnGrayscale = findViewById(R.id.btnGrayscale)
        btnEdges = findViewById(R.id.btnEdges)
        btnCapture = findViewById(R.id.btnCapture)
        toggleButton = findViewById(R.id.toggleButton)
        sensitivityPanel = findViewById(R.id.sensitivityPanel)
        sensitivitySlider = findViewById(R.id.sensitivitySlider)

        animateTitleGlow()

        // --- Setup GLSurfaceView and Renderer ---
        glSurfaceView.setEGLContextClientVersion(2)
        glSurfaceView.preserveEGLContextOnPause = true

        cameraRenderer = CameraRenderer()
        cameraRenderer?.setGLSurfaceView(glSurfaceView)
        glSurfaceView.setRenderer(cameraRenderer)

        // FIX: Use RENDERMODE_WHEN_DIRTY for efficiency. The renderer will request a render when needed.
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        // --- Setup Camera Manager and Callbacks ---
        cameraManager = CameraManager(this, glSurfaceView)

        cameraManager?.setFpsCallback { fps ->
            runOnUiThread {
                // Use Locale-aware formatting for user-facing text
                fpsText.text = "FPS: ${String.format(Locale.getDefault(), "%.1f", fps)}"

                val color = when {
                    fps >= 25 -> 0xFF00FF88.toInt()
                    fps >= 15 -> 0xFFFFD700.toInt()
                    else -> 0xFFFF6B6B.toInt()
                }
                fpsText.setTextColor(color)
            }
        }

        cameraManager?.setProcessedFrameCallback { pixels, width, height ->
            cameraRenderer?.updateProcessedTexture(pixels, width, height)
        }

        cameraManager?.setOnCameraReadyCallback { textureId ->
            val surfaceTexture = cameraManager?.getSurfaceTexture() ?: return@setOnCameraReadyCallback
            cameraRenderer?.setSurfaceTexture(surfaceTexture, textureId)
            // Renderer handles rotation/viewport updates itself
        }

        // --- Setup UI Listeners ---
        setupClickListeners()

        setMode(0) // Set initial mode
        checkCameraPermission()
    }

    private fun setupClickListeners() {
        btnRaw.setOnClickListener {
            setMode(0)
            vibrateClick()
        }

        btnGrayscale.setOnClickListener {
            setMode(1)
            vibrateClick()
        }

        btnEdges.setOnClickListener {
            setMode(2)
            vibrateClick()
        }

        btnCapture.setOnClickListener {
            captureFrame()
            vibrateClick()
        }

        toggleButton.setOnClickListener {
            currentMode = (currentMode + 1) % 3
            setMode(currentMode)
            vibrateClick()
        }

        sensitivitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                vibrateClick()
            }
        })
    }

    private fun setMode(mode: Int) {
        currentMode = mode

        val enabled = mode > 0
        cameraManager?.edgeDetectionEnabled = enabled
        cameraRenderer?.edgeDetectionEnabled = enabled

        val modeNames = arrayOf("RAW", "GRAY", "EDGES")
        val modeColors = arrayOf(0xFF5555FF.toInt(), 0xFF888888.toInt(), 0xFF00FFD1.toInt())

        modeText.text = modeNames[mode]
        modeText.setTextColor(modeColors[mode])

        toggleButton.text = "MODE: ${modeNames[mode]}"

        animateViewVisibility(sensitivityPanel, mode == 2)

        resetButtonStyles()
        when (mode) {
            0 -> btnRaw.alpha = 1.0f
            1 -> btnGrayscale.alpha = 1.0f
            2 -> btnEdges.alpha = 1.0f
        }

        animateModeChange()
    }

    private fun resetButtonStyles() {
        btnRaw.alpha = 0.6f
        btnGrayscale.alpha = 0.6f
        btnEdges.alpha = 0.6f
    }

    private fun animateModeChange() {
        val scaleX = ObjectAnimator.ofFloat(modeText, "scaleX", 1.0f, 1.3f, 1.0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(modeText, "scaleY", 1.0f, 1.3f, 1.0f).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
        }
        scaleX.start()
        scaleY.start()
    }

    private fun animateTitleGlow() {
        ObjectAnimator.ofFloat(titleText, "alpha", 1.0f, 0.5f, 1.0f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
        }.start()
    }

    private fun animateViewVisibility(view: View, show: Boolean) {
        val targetAlpha = if (show) 1f else 0f
        if (show && view.visibility != View.VISIBLE) {
            view.alpha = 0f
            view.visibility = View.VISIBLE
        }
        view.animate()
            .alpha(targetAlpha)
            .setDuration(300)
            .withEndAction {
                if (!show) {
                    view.visibility = View.GONE
                }
            }
            .start()
    }

    private fun vibrateClick() {
        if (::vibrator.isInitialized && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50L)
            }
        }
    }

    private fun captureFrame() {
        // TODO: Implement frame capture logic here.
        // This would involve getting the frame from CameraManager or reading pixels from the GL context.
        Toast.makeText(this, "📸 Frame capture not implemented!", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()

        if (cameraManager?.hasCameraPermission() == true) {
            glSurfaceView.post {
                cameraManager?.onResume(glSurfaceView.width, glSurfaceView.height)
                // Renderer handles rotation/viewport updates itself
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Requesting a render is enough to let the renderer update its viewport if needed.
        glSurfaceView.requestRender()
    }

    override fun onPause() {
        super.onPause()
        cameraManager?.onPause()
        glSurfaceView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Use the public lifecycle method instead of calling a private closeCamera()
        cameraManager?.onPause()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✅ Camera ready!", Toast.LENGTH_SHORT).show()
                // Start the camera now that permission is granted
                glSurfaceView.post {
                    cameraManager?.onResume(glSurfaceView.width, glSurfaceView.height)
                }
            } else {
                Toast.makeText(this, "❌ Camera permission is required to use this app.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
