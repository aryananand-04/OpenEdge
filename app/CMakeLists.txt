cmake_minimum_required(VERSION 3.10.2)
project("openedge")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add source files
add_library(native-lib SHARED
            src/main/cpp/native-lib.cpp
            src/main/cpp/processor/FrameProcessor.cpp
            src/main/cpp/utils/YUVConverter.cpp
            src/main/cpp/renderer/GLRenderer.cpp)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)
find_library(EGL-lib EGL)
find_library(GLESv2-lib GLESv2)

# OpenCV configuration
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/../../OpenCV-android-sdk/sdk/native/jni)
find_package(OpenCV REQUIRED)
include_directories(${OpenCV_INCLUDE_DIRS})

# Include directories
include_directories(
    src/main/cpp
    src/main/cpp/processor
    src/main/cpp/utils
    src/main/cpp/renderer
)

# Link libraries
target_link_libraries(native-lib
    ${OpenCV_LIBS}
    ${log-lib}
    ${android-lib}
    ${EGL-lib}
    ${GLESv2-lib}
)

