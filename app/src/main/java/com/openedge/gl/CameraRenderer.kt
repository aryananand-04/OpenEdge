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

        // Shader for the camera texture, which requires a transform matrix (uSTMatrix)
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

        // A simpler shader for the processed texture, which does NOT need a transform matrix
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

        // Fragment shader for the camera's external texture
        private const val FRAGMENT_SHADER_CAMERA = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """

        // Fragment shader for standard 2D textures (our processed frame)
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
            0.0f, 0.0f, // Flipped vertically to match camera/bitmap coordinates
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f
        )
    }

    private var surfaceTexture: SurfaceTexture? = null
    private var glSurfaceView: GLSurfaceView? = null

    // Texture IDs
    private var cameraTextureId = 0
    private var processedTextureId = 0

    // Shader Programs
    private var programCamera = 0
    private var programProcessed = 0

    // Handles for the Camera Program
    private var positionHandleCamera = 0
    private var textureCoordHandleCamera = 0
    private var mvpMatrixHandleCamera = 0
    private var stMatrixHandleCamera = 0

    // Handles for the Processed Program
    private var positionHandleProcessed = 0
    private var textureCoordHandleProcessed = 0
    private var mvpMatrixHandleProcessed = 0

    // Matrices
    private val mvpMatrix = FloatArray(16)
    private val stMatrix = FloatArray(16)

    // Buffers
    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().put(VERTEX_COORDS).also { it.position(0) }

    private val textureBuffer: FloatBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().put(TEXTURE_COORDS).also { it.position(0) }

    @Volatile
    var edgeDetectionEnabled = false

    fun setGLSurfaceView(view: GLSurfaceView) {
        glSurfaceView = view
    }

    fun setSurfaceTexture(texture: SurfaceTexture, id: Int) {
        surfaceTexture = texture
        cameraTextureId = id
    }

    private fun updateMVPMatrix() {
        Matrix.orthoM(mvpMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)
    }

    fun updateProcessedTexture(pixels: IntArray, width: Int, height: Int) {
        val buffer = ByteBuffer.allocateDirect(pixels.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .put(pixels)
            .position(0)

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
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            glSurfaceView?.requestRender()
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Compile shaders
        val vertexShaderCamera = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CAMERA)
        val vertexShaderProcessed = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_PROCESSED)
        val fragmentShaderCamera = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CAMERA)
        val fragmentShaderProcessed = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_PROCESSED)

        // Link the camera program
        programCamera = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShaderCamera)
            GLES20.glAttachShader(it, fragmentShaderCamera)
            GLES20.glLinkProgram(it)
        }
        // Get handles for camera program
        positionHandleCamera = GLES20.glGetAttribLocation(programCamera, "aPosition")
        textureCoordHandleCamera = GLES20.glGetAttribLocation(programCamera, "aTexCoord")
        mvpMatrixHandleCamera = GLES20.glGetUniformLocation(programCamera, "uMVPMatrix")
        stMatrixHandleCamera = GLES20.glGetUniformLocation(programCamera, "uSTMatrix")

        // Link the processed program
        programProcessed = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShaderProcessed)
            GLES20.glAttachShader(it, fragmentShaderProcessed)
            GLES20.glLinkProgram(it)
        }
        // Get handles for processed program
        positionHandleProcessed = GLES20.glGetAttribLocation(programProcessed, "aPosition")
        textureCoordHandleProcessed = GLES20.glGetAttribLocation(programProcessed, "aTexCoord")
        mvpMatrixHandleProcessed = GLES20.glGetUniformLocation(programProcessed, "uMVPMatrix")

        // Delete shaders after linking as they are no longer needed
        GLES20.glDeleteShader(vertexShaderCamera)
        GLES20.glDeleteShader(vertexShaderProcessed)
        GLES20.glDeleteShader(fragmentShaderCamera)
        GLES20.glDeleteShader(fragmentShaderProcessed)

        Matrix.setIdentityM(stMatrix, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        updateMVPMatrix()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Determine which program and texture to use for this frame
        val useProcessed = edgeDetectionEnabled && processedTextureId != 0

        if (useProcessed) {
            // ---- DRAW THE PROCESSED FRAME ----
            GLES20.glUseProgram(programProcessed)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)

            GLES20.glUniformMatrix4fv(mvpMatrixHandleProcessed, 1, false, mvpMatrix, 0)

            GLES20.glVertexAttribPointer(positionHandleProcessed, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glVertexAttribPointer(textureCoordHandleProcessed, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)

            GLES20.glEnableVertexAttribArray(positionHandleProcessed)
            GLES20.glEnableVertexAttribArray(textureCoordHandleProcessed)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(positionHandleProcessed)
            GLES20.glDisableVertexAttribArray(textureCoordHandleProcessed)
        } else {
            // ---- DRAW THE LIVE CAMERA PREVIEW ----
            surfaceTexture?.updateTexImage()
            surfaceTexture?.getTransformMatrix(stMatrix)

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




//package com.openedge.gl
//
//import android.graphics.SurfaceTexture
//import android.opengl.GLES11Ext
//import android.opengl.GLES20
//import android.opengl.GLSurfaceView
//import android.opengl.Matrix
//import android.util.Log
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import java.nio.FloatBuffer
//import javax.microedition.khronos.egl.EGLConfig
//import javax.microedition.khronos.opengles.GL10
//
//class CameraRenderer : GLSurfaceView.Renderer {
//    companion object {
//        private const val TAG = "CameraRenderer"
//
//        private const val VERTEX_SHADER = """
//            attribute vec4 aPosition;
//            attribute vec4 aTextureCoord;
//            varying vec2 vTextureCoord;
//            uniform mat4 uMVPMatrix;
//            uniform mat4 uSTMatrix;
//            void main() {
//                gl_Position = uMVPMatrix * aPosition;
//                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
//            }
//        """
//
//        private const val FRAGMENT_SHADER_CAMERA = """
//            #extension GL_OES_EGL_image_external : require
//            precision mediump float;
//            varying vec2 vTextureCoord;
//            uniform samplerExternalOES sTexture;
//            void main() {
//                gl_FragColor = texture2D(sTexture, vTextureCoord);
//            }
//        """
//
//        private const val FRAGMENT_SHADER_PROCESSED = """
//            precision mediump float;
//            varying vec2 vTextureCoord;
//            uniform sampler2D sTexture;
//            void main() {
//                gl_FragColor = texture2D(sTexture, vTextureCoord);
//            }
//        """
//
//        private val VERTEX_COORDS = floatArrayOf(
//            -1.0f, -1.0f,
//             1.0f, -1.0f,
//            -1.0f,  1.0f,
//             1.0f,  1.0f
//        )
//
//        private val TEXTURE_COORDS = floatArrayOf(
//            0.0f, 1.0f,
//            1.0f, 1.0f,
//            0.0f, 0.0f,
//            1.0f, 0.0f
//        )
//    }
//
//    private var surfaceTexture: SurfaceTexture? = null
//    private var cameraTextureId = 0
//    private var processedTextureId = 0
//
//    private var programCamera = 0
//    private var programProcessed = 0
//    private var currentProgram = 0
//
//    private var positionHandle = 0
//    private var textureCoordHandle = 0
//    private var mvpMatrixHandle = 0
//    private var stMatrixHandle = 0
//
//    private val mvpMatrix = FloatArray(16)
//    private val projectionMatrix = FloatArray(16)
//    private val stMatrix = FloatArray(16)
//
//    private var vertexBuffer: FloatBuffer? = null
//    private var textureBuffer: FloatBuffer? = null
//
//    private var displayRotation = 0
//    private var glSurfaceView: GLSurfaceView? = null
//
//    @Volatile
//    var edgeDetectionEnabled = false
//        set(value) {
//            field = value
//            currentProgram = if (value && processedTextureId != 0) programProcessed else programCamera
//        }
//
//    fun setGLSurfaceView(view: GLSurfaceView) {
//        glSurfaceView = view
//    }
//
//    fun setSurfaceTexture(texture: SurfaceTexture, id: Int) {
//        surfaceTexture = texture
//        cameraTextureId = id
//        Matrix.setIdentityM(stMatrix, 0)
//    }
//
//    fun setDisplayRotation(rotation: Int) {
//        displayRotation = rotation
//        updateTransformMatrix()
//    }
//
//    fun updateProcessedTexture(pixels: IntArray, width: Int, height: Int) {
//        glSurfaceView?.queueEvent {
//            if (processedTextureId == 0) {
//                val textures = IntArray(1)
//                GLES20.glGenTextures(1, textures, 0)
//                processedTextureId = textures[0]
//
//                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)
//                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
//                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
//                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
//                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
//            }
//
//            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)
//            val buffer = ByteBuffer.allocateDirect(pixels.size * 4)
//            buffer.order(ByteOrder.nativeOrder())
//            buffer.asIntBuffer().put(pixels)
//            buffer.position(0)
//
//            GLES20.glTexImage2D(
//                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
//                width, height, 0,
//                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
//            )
//
//            if (edgeDetectionEnabled) {
//                currentProgram = programProcessed
//            }
//
//            glSurfaceView?.requestRender()
//        }
//    }
//
//    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
//        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
//
//        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
//        val fragmentCamera = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CAMERA)
//        val fragmentProcessed = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_PROCESSED)
//
//        programCamera = GLES20.glCreateProgram()
//        GLES20.glAttachShader(programCamera, vertexShader)
//        GLES20.glAttachShader(programCamera, fragmentCamera)
//        GLES20.glLinkProgram(programCamera)
//
//        programProcessed = GLES20.glCreateProgram()
//        GLES20.glAttachShader(programProcessed, vertexShader)
//        GLES20.glAttachShader(programProcessed, fragmentProcessed)
//        GLES20.glLinkProgram(programProcessed)
//
//        currentProgram = programCamera
//
//        positionHandle = GLES20.glGetAttribLocation(programCamera, "aPosition")
//        textureCoordHandle = GLES20.glGetAttribLocation(programCamera, "aTextureCoord")
//        mvpMatrixHandle = GLES20.glGetUniformLocation(programCamera, "uMVPMatrix")
//        stMatrixHandle = GLES20.glGetUniformLocation(programCamera, "uSTMatrix")
//
//        vertexBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.size * 4)
//            .order(ByteOrder.nativeOrder())
//            .asFloatBuffer()
//            .apply {
//                put(VERTEX_COORDS)
//                position(0)
//            }
//
//        textureBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.size * 4)
//            .order(ByteOrder.nativeOrder())
//            .asFloatBuffer()
//            .apply {
//                put(TEXTURE_COORDS)
//                position(0)
//            }
//
//        Matrix.setIdentityM(mvpMatrix, 0)
//        Matrix.setIdentityM(projectionMatrix, 0)
//        Matrix.setIdentityM(stMatrix, 0)
//    }
//
//    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
//        GLES20.glViewport(0, 0, width, height)
//        updateTransformMatrix()
//    }
//
//    private fun updateTransformMatrix() {
//        // Simple orthographic projection
//        Matrix.orthoM(projectionMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)
//
//        // Simple rotation: portrait = 90°, landscape = 0°
//        val rotation = if (displayRotation == 90 || displayRotation == 270) 90f else 0f
//
//        val modelMatrix = FloatArray(16)
//        Matrix.setIdentityM(modelMatrix, 0)
//        Matrix.rotateM(modelMatrix, 0, rotation, 0f, 0f, 1f)
//
//        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelMatrix, 0)
//    }
//
//    override fun onDrawFrame(gl: GL10?) {
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
//
//        // Switch program if needed
//        if (edgeDetectionEnabled && processedTextureId != 0) {
//            if (currentProgram != programProcessed) {
//                currentProgram = programProcessed
//                mvpMatrixHandle = GLES20.glGetUniformLocation(programProcessed, "uMVPMatrix")
//            }
//        } else {
//            if (currentProgram != programCamera) {
//                currentProgram = programCamera
//                mvpMatrixHandle = GLES20.glGetUniformLocation(programCamera, "uMVPMatrix")
//                stMatrixHandle = GLES20.glGetUniformLocation(programCamera, "uSTMatrix")
//            }
//        }
//
//        GLES20.glUseProgram(currentProgram)
//        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
//
//        if (currentProgram == programCamera) {
//            // Camera texture
//            surfaceTexture?.let { texture ->
//                texture.updateTexImage()
//                texture.getTransformMatrix(stMatrix)
//                GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, stMatrix, 0)
//
//                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
//                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
//            }
//        } else {
//            // Processed texture
//            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
//            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)
//        }
//
//        GLES20.glEnableVertexAttribArray(positionHandle)
//        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
//
//        GLES20.glEnableVertexAttribArray(textureCoordHandle)
//        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
//
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
//
//        GLES20.glDisableVertexAttribArray(positionHandle)
//        GLES20.glDisableVertexAttribArray(textureCoordHandle)
//    }
//
//    private fun loadShader(type: Int, shaderCode: String): Int {
//        val shader = GLES20.glCreateShader(type)
//        GLES20.glShaderSource(shader, shaderCode)
//        GLES20.glCompileShader(shader)
//
//        val compiled = IntArray(1)
//        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
//        if (compiled[0] == 0) {
//            val error = GLES20.glGetShaderInfoLog(shader)
//            Log.e(TAG, "Shader error: $error")
//            GLES20.glDeleteShader(shader)
//            return 0
//        }
//        return shader
//    }
//}
//
//package com.openedge.gl
//
//import android.graphics.SurfaceTexture
//import android.opengl.GLES11Ext
//import android.opengl.GLES20
//import android.opengl.GLSurfaceView
//import android.opengl.Matrix
//import android.util.Log
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import java.nio.FloatBuffer
//import javax.microedition.khronos.egl.EGLConfig
//import javax.microedition.khronos.opengles.GL10
//
//class CameraRenderer : GLSurfaceView.Renderer {
//    companion object {
//        private const val TAG = "CameraRenderer"
//
//        private const val VERTEX_SHADER = """
//            attribute vec4 aPosition;
//            attribute vec4 aTextureCoord;
//            varying vec2 vTextureCoord;
//            uniform mat4 uMVPMatrix;
//            uniform mat4 uSTMatrix;
//            void main() {
//                gl_Position = uMVPMatrix * aPosition;
//                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
//            }
//        """
//
//        private const val FRAGMENT_SHADER_CAMERA = """
//            #extension GL_OES_EGL_image_external : require
//            precision mediump float;
//            varying vec2 vTextureCoord;
//            uniform samplerExternalOES sTexture;
//            void main() {
//                gl_FragColor = texture2D(sTexture, vTextureCoord);
//            }
//        """
//
//        private const val FRAGMENT_SHADER_PROCESSED = """
//            precision mediump float;
//            varying vec2 vTextureCoord;
//            uniform sampler2D sTexture;
//            void main() {
//                gl_FragColor = texture2D(sTexture, vTextureCoord);
//            }
//        """
//
//        // Full-screen quad vertices
//        private val VERTEX_COORDS = floatArrayOf(
//            -1.0f, -1.0f,
//             1.0f, -1.0f,
//            -1.0f,  1.0f,
//             1.0f,  1.0f
//        )
//
//        // Texture coordinates
//        private val TEXTURE_COORDS = floatArrayOf(
//            0.0f, 1.0f,
//            1.0f, 1.0f,
//            0.0f, 0.0f,
//            1.0f, 0.0f
//        )
//    }
//
//    private var surfaceTexture: SurfaceTexture? = null
//    private var cameraTextureId = 0
//    private var processedTextureId = 0
//
//    private var programCamera = 0
//    private var programProcessed = 0
//    private var currentProgram = 0
//
//    private var positionHandle = 0
//    private var textureCoordHandle = 0
//    private var mvpMatrixHandle = 0
//    private var stMatrixHandle = 0
//
//    private val mvpMatrix = FloatArray(16)
//    private val stMatrix = FloatArray(16)
//
//    private var vertexBuffer: FloatBuffer? = null
//    private var textureBuffer: FloatBuffer? = null
//
//    private var glSurfaceView: GLSurfaceView? = null
//    private var viewWidth = 0
//    private var viewHeight = 0
//
//    @Volatile
//    var edgeDetectionEnabled = false
//        set(value) {
//            field = value
//            currentProgram = if (value && processedTextureId != 0) programProcessed else programCamera
//        }
//
//    fun setGLSurfaceView(view: GLSurfaceView) {
//        glSurfaceView = view
//    }
//
//    fun setSurfaceTexture(texture: SurfaceTexture, id: Int) {
//        surfaceTexture = texture
//        cameraTextureId = id
//        Matrix.setIdentityM(stMatrix, 0)
//    }
//
//    fun setDisplayRotation(rotation: Int) {
//        // Rotation is now handled by proper texture matrix from camera
//        // We just need to set up the MVP matrix for the view
//        updateMVPMatrix()
//    }
//
//    private fun updateMVPMatrix() {
//        // Simple orthographic projection - no rotation needed here
//        // Camera rotation is handled by the camera's texture transform matrix
//        Matrix.orthoM(mvpMatrix, 0, -1f, 1f, -1f, 1f, -1f, 1f)
//    }
//
//    fun updateProcessedTexture(pixels: IntArray, width: Int, height: Int) {
//        glSurfaceView?.queueEvent {
//            if (processedTextureId == 0) {
//                val textures = IntArray(1)
//                GLES20.glGenTextures(1, textures, 0)
//                processedTextureId = textures[0]
//
//                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)
//                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
//                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
//                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
//                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
//            }
//
//            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)
//            val buffer = ByteBuffer.allocateDirect(pixels.size * 4)
//            buffer.order(ByteOrder.nativeOrder())
//            buffer.asIntBuffer().put(pixels)
//            buffer.position(0)
//
//            GLES20.glTexImage2D(
//                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
//                width, height, 0,
//                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
//            )
//
//            if (edgeDetectionEnabled) {
//                currentProgram = programProcessed
//            }
//
//            glSurfaceView?.requestRender()
//        }
//    }
//
//    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
//        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
//
//        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
//        val fragmentCamera = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CAMERA)
//        val fragmentProcessed = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_PROCESSED)
//
//        programCamera = GLES20.glCreateProgram()
//        GLES20.glAttachShader(programCamera, vertexShader)
//        GLES20.glAttachShader(programCamera, fragmentCamera)
//        GLES20.glLinkProgram(programCamera)
//
//        programProcessed = GLES20.glCreateProgram()
//        GLES20.glAttachShader(programProcessed, vertexShader)
//        GLES20.glAttachShader(programProcessed, fragmentProcessed)
//        GLES20.glLinkProgram(programProcessed)
//
//        currentProgram = programCamera
//
//        vertexBuffer = ByteBuffer.allocateDirect(VERTEX_COORDS.size * 4)
//            .order(ByteOrder.nativeOrder())
//            .asFloatBuffer()
//            .apply {
//                put(VERTEX_COORDS)
//                position(0)
//            }
//
//        textureBuffer = ByteBuffer.allocateDirect(TEXTURE_COORDS.size * 4)
//            .order(ByteOrder.nativeOrder())
//            .asFloatBuffer()
//            .apply {
//                put(TEXTURE_COORDS)
//                position(0)
//            }
//
//        Matrix.setIdentityM(mvpMatrix, 0)
//        Matrix.setIdentityM(stMatrix, 0)
//    }
//
//    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
//        viewWidth = width
//        viewHeight = height
//        GLES20.glViewport(0, 0, width, height)
//        updateMVPMatrix()
//    }
//
//    override fun onDrawFrame(gl: GL10?) {
//        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
//
//        // Switch program if needed
//        if (edgeDetectionEnabled && processedTextureId != 0) {
//            if (currentProgram != programProcessed) {
//                currentProgram = programProcessed
//            }
//        } else {
//            if (currentProgram != programCamera) {
//                currentProgram = programCamera
//            }
//        }
//
//        GLES20.glUseProgram(currentProgram)
//
//        // Get attribute/uniform locations
//        positionHandle = GLES20.glGetAttribLocation(currentProgram, "aPosition")
//        textureCoordHandle = GLES20.glGetAttribLocation(currentProgram, "aTexCoord")
//        mvpMatrixHandle = GLES20.glGetUniformLocation(currentProgram, "uMVPMatrix")
//
//        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
//
//        if (currentProgram == programCamera) {
//            // Camera texture
//            surfaceTexture?.let { texture ->
//                texture.updateTexImage()
//                texture.getTransformMatrix(stMatrix)
//
//                stMatrixHandle = GLES20.glGetUniformLocation(programCamera, "uSTMatrix")
//                GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, stMatrix, 0)
//
//                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
//                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
//            }
//        } else {
//            // Processed texture
//            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
//            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, processedTextureId)
//        }
//
//        GLES20.glEnableVertexAttribArray(positionHandle)
//        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
//
//        GLES20.glEnableVertexAttribArray(textureCoordHandle)
//        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
//
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
//
//        GLES20.glDisableVertexAttribArray(positionHandle)
//        GLES20.glDisableVertexAttribArray(textureCoordHandle)
//    }
//
//    private fun loadShader(type: Int, shaderCode: String): Int {
//        val shader = GLES20.glCreateShader(type)
//        GLES20.glShaderSource(shader, shaderCode)
//        GLES20.glCompileShader(shader)
//
//        val compiled = IntArray(1)
//        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
//        if (compiled[0] == 0) {
//            val error = GLES20.glGetShaderInfoLog(shader)
//            Log.e(TAG, "Shader error: $error")
//            GLES20.glDeleteShader(shader)
//            return 0
//        }
//        return shader
//    }
//}
