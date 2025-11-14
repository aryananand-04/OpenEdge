#ifndef GLRENDERER_H
#define GLRENDERER_H

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <android/log.h>
#include <opencv2/opencv.hpp>
#include <mutex>

/**
 * OpenGL ES 2.0 renderer for displaying processed camera frames.
 * Handles texture creation, shader compilation, and rendering.
 */
class GLRenderer {
public:
    GLRenderer();
    ~GLRenderer();
    
    /**
     * Initialize OpenGL resources (shaders, textures, buffers).
     * @param width Viewport width
     * @param height Viewport height
     */
    bool init(int width, int height);
    
    /**
     * Clean up OpenGL resources.
     */
    void cleanup();
    
    /**
     * Update frame data (called from any thread).
     * Stores the frame for rendering on GL thread.
     * @param frame Processed BGR frame from OpenCV
     */
    void updateFrame(const cv::Mat& frame);
    
    /**
     * Render the current frame (must be called from GL thread).
     */
    void renderFrame();
    
    /**
     * Update viewport dimensions.
     */
    void setViewport(int width, int height);
    
private:
    // Shader program
    GLuint shaderProgram;
    
    // Texture
    GLuint textureId;
    int textureWidth;
    int textureHeight;
    
    // Viewport
    int viewportWidth;
    int viewportHeight;
    
    // Shader locations
    GLint positionHandle;
    GLint texCoordHandle;
    GLint textureHandle;
    
    // Frame storage (protected by mutex for thread safety)
    std::mutex frameMutex;
    cv::Mat pendingFrame;
    bool hasPendingFrame;
    
    /**
     * Compile shader from source.
     */
    GLuint compileShader(GLenum type, const char* source);
    
    /**
     * Create shader program from vertex and fragment shaders.
     */
    GLuint createProgram(const char* vertexSource, const char* fragmentSource);
    
    /**
     * Create texture from OpenCV Mat.
     */
    void updateTexture(const cv::Mat& frame);
    
    /**
     * Vertex shader source code.
     */
    static const char* vertexShaderSource;
    
    /**
     * Fragment shader source code.
     */
    static const char* fragmentShaderSource;
};

#endif

