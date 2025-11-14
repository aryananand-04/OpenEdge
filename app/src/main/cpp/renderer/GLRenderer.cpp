#include "GLRenderer.h"
#include <cstring>
#include <mutex>

#define LOG_TAG "GLRenderer"
#define ALOG(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Vertex shader: simple quad with texture coordinates
const char* GLRenderer::vertexShaderSource = R"(
attribute vec4 aPosition;
attribute vec2 aTexCoord;
varying vec2 vTexCoord;

void main() {
    gl_Position = aPosition;
    vTexCoord = aTexCoord;
}
)";

// Fragment shader: sample texture
const char* GLRenderer::fragmentShaderSource = R"(
precision mediump float;
uniform sampler2D uTexture;
varying vec2 vTexCoord;

void main() {
    gl_FragColor = texture2D(uTexture, vTexCoord);
}
)";

// Quad vertices (full screen)
static const float quadVertices[] = {
    // Position (x, y)    Texture (u, v)
    -1.0f, -1.0f,         0.0f, 1.0f,  // Bottom-left
     1.0f, -1.0f,         1.0f, 1.0f,  // Bottom-right
    -1.0f,  1.0f,         0.0f, 0.0f,  // Top-left
     1.0f,  1.0f,         1.0f, 0.0f   // Top-right
};

GLRenderer::GLRenderer()
    : shaderProgram(0)
    , textureId(0)
    , textureWidth(0)
    , textureHeight(0)
    , viewportWidth(0)
    , viewportHeight(0)
    , positionHandle(-1)
    , texCoordHandle(-1)
    , textureHandle(-1)
    , hasPendingFrame(false)
{
    ALOG("GLRenderer created");
}

GLRenderer::~GLRenderer() {
    cleanup();
    ALOG("GLRenderer destroyed");
}

bool GLRenderer::init(int width, int height) {
    ALOG("Initializing GLRenderer: %dx%d", width, height);
    
    viewportWidth = width;
    viewportHeight = height;
    
    // Compile shaders and create program
    shaderProgram = createProgram(vertexShaderSource, fragmentShaderSource);
    if (shaderProgram == 0) {
        ALOGE("Failed to create shader program");
        return false;
    }
    
    // Get attribute/uniform locations
    positionHandle = glGetAttribLocation(shaderProgram, "aPosition");
    texCoordHandle = glGetAttribLocation(shaderProgram, "aTexCoord");
    textureHandle = glGetUniformLocation(shaderProgram, "uTexture");
    
    if (positionHandle < 0 || texCoordHandle < 0 || textureHandle < 0) {
        ALOGE("Failed to get shader locations");
        return false;
    }
    
    // Generate texture
    glGenTextures(1, &textureId);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    
    // Set viewport
    glViewport(0, 0, width, height);
    
    // Enable blending (optional, for transparency)
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    
    ALOG("GLRenderer initialized successfully");
    return true;
}

void GLRenderer::cleanup() {
    if (textureId != 0) {
        glDeleteTextures(1, &textureId);
        textureId = 0;
    }
    
    if (shaderProgram != 0) {
        glDeleteProgram(shaderProgram);
        shaderProgram = 0;
    }
    
    ALOG("GLRenderer cleaned up");
}

void GLRenderer::setViewport(int width, int height) {
    viewportWidth = width;
    viewportHeight = height;
    glViewport(0, 0, width, height);
    ALOG("Viewport set to %dx%d", width, height);
}

void GLRenderer::updateFrame(const cv::Mat& frame) {
    std::lock_guard<std::mutex> lock(frameMutex);
    if (!frame.empty()) {
        frame.copyTo(pendingFrame);
        hasPendingFrame = true;
    }
}

void GLRenderer::renderFrame() {
    if (shaderProgram == 0) {
        return;
    }
    
    // Get pending frame (thread-safe)
    cv::Mat frameToRender;
    {
        std::lock_guard<std::mutex> lock(frameMutex);
        if (hasPendingFrame && !pendingFrame.empty()) {
            frameToRender = pendingFrame.clone();
            hasPendingFrame = false;
        }
    }
    
    if (frameToRender.empty()) {
        // No frame to render, just clear
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
        return;
    }
    
    // Update texture with new frame data
    updateTexture(frameToRender);
    
    // Clear screen
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    
    // Use shader program
    glUseProgram(shaderProgram);
    
    // Bind texture
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, textureId);
    glUniform1i(textureHandle, 0);
    
    // Set up vertex attributes
    // Position (x, y)
    glEnableVertexAttribArray(positionHandle);
    glVertexAttribPointer(positionHandle, 2, GL_FLOAT, GL_FALSE, 
                          4 * sizeof(float), quadVertices);
    
    // Texture coordinates (u, v)
    glEnableVertexAttribArray(texCoordHandle);
    glVertexAttribPointer(texCoordHandle, 2, GL_FLOAT, GL_FALSE,
                          4 * sizeof(float), quadVertices + 2);
    
    // Draw quad
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    
    // Disable attributes
    glDisableVertexAttribArray(positionHandle);
    glDisableVertexAttribArray(texCoordHandle);
}

void GLRenderer::updateTexture(const cv::Mat& frame) {
    if (frame.empty()) {
        return;
    }
    
    int width = frame.cols;
    int height = frame.rows;
    
    // Convert BGR to RGB for OpenGL
    cv::Mat rgbFrame;
    cv::cvtColor(frame, rgbFrame, cv::COLOR_BGR2RGB);
    
    // Bind texture
    glBindTexture(GL_TEXTURE_2D, textureId);
    
    // Update texture if size changed
    if (width != textureWidth || height != textureHeight) {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0,
                     GL_RGB, GL_UNSIGNED_BYTE, rgbFrame.data);
        textureWidth = width;
        textureHeight = height;
        ALOG("Texture resized to %dx%d", width, height);
    } else {
        // Just update data
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height,
                        GL_RGB, GL_UNSIGNED_BYTE, rgbFrame.data);
    }
}

GLuint GLRenderer::compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    if (shader == 0) {
        ALOGE("Failed to create shader");
        return 0;
    }
    
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    
    // Check compilation status
    GLint compiled = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            char* infoLog = new char[infoLen];
            glGetShaderInfoLog(shader, infoLen, nullptr, infoLog);
            ALOGE("Shader compilation failed: %s", infoLog);
            delete[] infoLog;
        }
        glDeleteShader(shader);
        return 0;
    }
    
    return shader;
}

GLuint GLRenderer::createProgram(const char* vertexSource, const char* fragmentSource) {
    GLuint vertexShader = compileShader(GL_VERTEX_SHADER, vertexSource);
    if (vertexShader == 0) {
        return 0;
    }
    
    GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentSource);
    if (fragmentShader == 0) {
        glDeleteShader(vertexShader);
        return 0;
    }
    
    GLuint program = glCreateProgram();
    if (program == 0) {
        ALOGE("Failed to create program");
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return 0;
    }
    
    glAttachShader(program, vertexShader);
    glAttachShader(program, fragmentShader);
    glLinkProgram(program);
    
    // Check linking status
    GLint linked = 0;
    glGetProgramiv(program, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(program, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            char* infoLog = new char[infoLen];
            glGetProgramInfoLog(program, infoLen, nullptr, infoLog);
            ALOGE("Program linking failed: %s", infoLog);
            delete[] infoLog;
        }
        glDeleteProgram(program);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return 0;
    }
    
    // Clean up shaders (they're linked into the program now)
    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);
    
    return program;
}

