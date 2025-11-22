package com.openedge.gl

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraRenderer : GLSurfaceView.Renderer {
    companion object {
        private const val TAG = "CameraRenderer"
        
        // Vertex shader for camera texture
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """
        
        // Fragment shader for external texture (camera)
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
        
        // Full screen quad coordinates
        private val VERTEX_COORDS = floatArrayOf(
            -1.0f, -1.0f,  // Bottom left
             1.0f, -1.0f,  // Bottom right
            -1.0f,  1.0f,  // Top left
             1.0f,  1.0f   // Top right
        )
        
        private val TEXTURE_COORDS = floatArrayOf(
            0.0f, 1.0f,  // Bottom left
            1.0f, 1.0f,  // Bottom right
            0.0f, 0.0f,  // Top left
            1.0f, 0.0f   // Top right
        )
    }

    private var surfaceTexture: SurfaceTexture? = null
    private var textureId = 0
    
    // Shader program
    private var program = 0
    private var positionHandle = 0
    private var textureCoordHandle = 0
    private var mvpMatrixHandle = 0
    private var stMatrixHandle = 0
    
    // Matrices
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16)
    
    // Vertex buffers
    private var vertexBuffer: FloatBuffer? = null
    private var textureBuffer: FloatBuffer? = null
    
    // Orientation info
    private var sensorOrientation = 0
    private var displayRotation = 0
    private var cameraFacing: Int? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    fun setSurfaceTexture(texture: SurfaceTexture, id: Int) {
        surfaceTexture = texture
        textureId = id
        // Initialize ST matrix to identity
        Matrix.setIdentityM(stMatrix, 0)
    }
    
    fun setOrientationInfo(sensorOrientation: Int, displayRotation: Int, cameraFacing: Int?) {
        this.sensorOrientation = sensorOrientation
        this.displayRotation = displayRotation
        this.cameraFacing = cameraFacing
        updateTransformMatrix()
        // Transform matrix updated - next frame will use new transform
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "Surface created")
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        
        // Compile shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        
        // Create program
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        // Get attribute/uniform locations
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        textureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        stMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
        
        // Setup vertex buffers
        vertexBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(VERTEX_COORDS)
                position(0)
            }
            
        textureBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(TEXTURE_COORDS)
                position(0)
            }
        
        // Initialize matrices to identity
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.setIdentityM(projectionMatrix, 0)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.setIdentityM(stMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: ${width}x${height}")
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
        updateTransformMatrix()
    }
    
    private fun updateTransformMatrix() {
        if (surfaceWidth == 0 || surfaceHeight == 0) return
        
        // Calculate the rotation needed based on sensor orientation and display rotation
        // For back camera: rotation = (sensorOrientation - displayRotation + 360) % 360
        // Add 90 degrees to fix upside-down issue
        val baseRotation = (sensorOrientation - displayRotation + 360) % 360
        val degrees = (baseRotation + 90) % 360
        
        Log.d(TAG, "Sensor orientation: $sensorOrientation, Display rotation: $displayRotation, Base rotation: $baseRotation, Final degrees: $degrees, Surface: ${surfaceWidth}x${surfaceHeight}")
        
        // Calculate surface aspect ratio (keep simple like before)
        val aspectRatio = surfaceHeight.toFloat() / surfaceWidth.toFloat()
        
        // Set up orthographic projection (simple like before)
        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -aspectRatio, aspectRatio, -1f, 1f)
        
        // Set view matrix to identity
        Matrix.setIdentityM(viewMatrix, 0)
        
        // Model matrix: Start with identity
        val modelMatrix = FloatArray(16)
        Matrix.setIdentityM(modelMatrix, 0)
        
        // Apply rotation to match device orientation (no flip/mirror)
        // This rotates the camera preview to match how the device is held
        Matrix.rotateM(modelMatrix, 0, degrees.toFloat(), 0f, 0f, 1f)
        
        // Combine: MVP = Projection * View * Model
        val temp = FloatArray(16)
        Matrix.multiplyMM(temp, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, temp, 0, modelMatrix, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        surfaceTexture?.let { texture ->
            // Update texture with latest frame
            texture.updateTexImage()
            
            // Get transform matrix from SurfaceTexture
            texture.getTransformMatrix(stMatrix)
            
            // Use shader program
            GLES20.glUseProgram(program)
            
            // Set matrices
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, stMatrix, 0)
            
            // Bind texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            
            // Enable vertex arrays
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(
                positionHandle, 2, GLES20.GL_FLOAT, false,
                0, vertexBuffer
            )
            
            GLES20.glEnableVertexAttribArray(textureCoordHandle)
            GLES20.glVertexAttribPointer(
                textureCoordHandle, 2, GLES20.GL_FLOAT, false,
                0, textureBuffer
            )
            
            // Draw
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            
            // Disable vertex arrays
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(textureCoordHandle)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Shader compilation error: $error")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun getTextureId(): Int = textureId
}
