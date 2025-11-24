package com.openedge.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraRenderer : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "CameraRenderer"

        private const val VERTEX_SHADER_CAMERA = """
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

        private const val VERTEX_SHADER_PROCESSED = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            uniform mat4 uMVPMatrix;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = aTextureCoord.xy;
            }
        """

        private const val FRAGMENT_SHADER_CAMERA = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """

        private const val FRAGMENT_SHADER_PROCESSED = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """

        private val VERTEX_COORDS = floatArrayOf(
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f,  1.0f,
            1.0f,  1.0f
        )

        private val TEXTURE_COORDS = floatArrayOf(
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
    }

    private var surfaceTexture: SurfaceTexture? = null
    private var glSurfaceView: GLSurfaceView? = null

    private var cameraTextureId = 0
    private var processedTextureId = 0

    private var programCamera = 0
    private var programProcessed = 0

    private var positionHandleCamera = 0
    private var textureCoordHandleCamera = 0
    private var mvpMatrixHandleCamera = 0
    private var stMatrixHandleCamera = 0

    private var positionHandleProcessed = 0
    private var textureCoordHandleProcessed = 0
    private var mvpMatrixHandleProcessed = 0

    private val mvpMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    private val processedMatrix = FloatArray(16)

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().put(VERTEX_COORDS).apply { position(0) }

    private val textureBuffer: FloatBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().put(TEXTURE_COORDS).apply { position(0) }

    @Volatile
    var edgeDetectionEnabled = false

    @Volatile
    var cameraRotation = 0

    fun setGLSurfaceView(view: GLSurfaceView) {
        glSurfaceView = view
    }

    fun setSurfaceTexture(texture: SurfaceTexture, id: Int) {
        surfaceTexture = texture
        cameraTextureId = id
        Log.d(TAG, "✅ SurfaceTexture set with ID: $id")
    }

    private fun updateMVPMatrix() {
        Matrix.orthoM(mvpMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)
    }

    // For RAW camera preview - apply rotation and horizontal flip
    private fun updateRotationMatrix() {
        Matrix.setIdentityM(rotationMatrix, 0)
        Matrix.rotateM(rotationMatrix, 0, cameraRotation.toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(rotationMatrix, 0, -1f, 1f, 1f) // Flip horizontally

        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, mvpMatrix, 0, rotationMatrix, 0)
        System.arraycopy(tempMatrix, 0, rotationMatrix, 0, 16)
    }

    // For processed frames - apply rotation and horizontal flip
    private fun updateProcessedMatrix() {
        Matrix.setIdentityM(processedMatrix, 0)
        Matrix.rotateM(processedMatrix, 0, cameraRotation.toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(processedMatrix, 0, -1f, 1f, 1f) // Flip horizontally

        val tempMatrix = FloatArray(16)
        Matrix.multiplyMM(tempMatrix, 0, mvpMatrix, 0, processedMatrix, 0)
        System.arraycopy(tempMatrix, 0, processedMatrix, 0, 16)
    }

    fun updateProcessedTexture(pixels: IntArray, width: Int, height: Int) {
        val buffer = ByteBuffer.allocateDirect(pixels.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .put(pixels)
            .apply { position(0) }

        glSurfaceView?.queueEvent {
            if (processedTextureId == 0) {
                val textures = IntArray(1)
                GLES20.glGenTextures(1, textures, 0)
                processedTextureId = textures[0]

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

                Log.d(TAG, "✅ Created processed texture ID: $processedTextureId")
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            glSurfaceView?.requestRender()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        val vertexShaderCamera = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CAMERA)
        val vertexShaderProcessed = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_PROCESSED)
        val fragmentShaderCamera = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CAMERA)
        val fragmentShaderProcessed = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_PROCESSED)

        programCamera = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShaderCamera)
            GLES20.glAttachShader(it, fragmentShaderCamera)
            GLES20.glLinkProgram(it)
        }

        positionHandleCamera = GLES20.glGetAttribLocation(programCamera, "aPosition")
        textureCoordHandleCamera = GLES20.glGetAttribLocation(programCamera, "aTextureCoord")
        mvpMatrixHandleCamera = GLES20.glGetUniformLocation(programCamera, "uMVPMatrix")
        stMatrixHandleCamera = GLES20.glGetUniformLocation(programCamera, "uSTMatrix")

        programProcessed = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShaderProcessed)
            GLES20.glAttachShader(it, fragmentShaderProcessed)
            GLES20.glLinkProgram(it)
        }

        positionHandleProcessed = GLES20.glGetAttribLocation(programProcessed, "aPosition")
        textureCoordHandleProcessed = GLES20.glGetAttribLocation(programProcessed, "aTextureCoord")
        mvpMatrixHandleProcessed = GLES20.glGetUniformLocation(programProcessed, "uMVPMatrix")

        GLES20.glDeleteShader(vertexShaderCamera)
        GLES20.glDeleteShader(vertexShaderProcessed)
        GLES20.glDeleteShader(fragmentShaderCamera)
        GLES20.glDeleteShader(fragmentShaderProcessed)

        Matrix.setIdentityM(stMatrix, 0)

        Log.d(TAG, "✅ Surface created, shaders compiled")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        updateMVPMatrix()
        Log.d(TAG, "✅ Surface changed: ${width}x${height}")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val useProcessed = edgeDetectionEnabled && processedTextureId != 0

        if (useProcessed) {
            // Draw processed texture with rotation and flip
            GLES20.glUseProgram(programProcessed)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)

            updateProcessedMatrix()
            GLES20.glUniformMatrix4fv(mvpMatrixHandleProcessed, 1, false, processedMatrix, 0)

            GLES20.glVertexAttribPointer(positionHandleProcessed, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glVertexAttribPointer(textureCoordHandleProcessed, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

            GLES20.glEnableVertexAttribArray(positionHandleProcessed)
            GLES20.glEnableVertexAttribArray(textureCoordHandleProcessed)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(positionHandleProcessed)
            GLES20.glDisableVertexAttribArray(textureCoordHandleProcessed)
        } else {
            // Draw live camera preview
            val texture = surfaceTexture
            if (texture != null && cameraTextureId != 0) {
                texture.updateTexImage()
                texture.getTransformMatrix(stMatrix)

                GLES20.glUseProgram(programCamera)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)

                GLES20.glUniformMatrix4fv(mvpMatrixHandleCamera, 1, false, mvpMatrix, 0)
                GLES20.glUniformMatrix4fv(stMatrixHandleCamera, 1, false, stMatrix, 0)

                GLES20.glVertexAttribPointer(positionHandleCamera, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
                GLES20.glVertexAttribPointer(textureCoordHandleCamera, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

                GLES20.glEnableVertexAttribArray(positionHandleCamera)
                GLES20.glEnableVertexAttribArray(textureCoordHandleCamera)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                GLES20.glDisableVertexAttribArray(positionHandleCamera)
                GLES20.glDisableVertexAttribArray(textureCoordHandleCamera)
            }
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                Log.e(TAG, "Could not compile shader $type: $error")
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Could not compile shader.")
            }
        }
    }
}