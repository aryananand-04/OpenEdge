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

# OpenCV configuration - try multiple possible paths
set(OpenCV_POSSIBLE_PATHS
    ${CMAKE_SOURCE_DIR}/../../OpenCV-android-sdk/sdk/native/jni
    ${CMAKE_SOURCE_DIR}/../../../OpenCV-android-sdk/sdk/native/jni
    ${CMAKE_CURRENT_SOURCE_DIR}/../../OpenCV-android-sdk/sdk/native/jni
)

set(OpenCV_DIR "")
foreach(PATH ${OpenCV_POSSIBLE_PATHS})
    if(EXISTS "${PATH}/OpenCVConfig.cmake")
        set(OpenCV_DIR "${PATH}")
        message(STATUS "Found OpenCV at: ${OpenCV_DIR}")
        break()
    endif()
endforeach()

if(OpenCV_DIR STREQUAL "")
    message(FATAL_ERROR "OpenCV not found! Please ensure OpenCV-android-sdk is in the project root.")
    message(FATAL_ERROR "Expected paths:")
    foreach(PATH ${OpenCV_POSSIBLE_PATHS})
        message(FATAL_ERROR "  ${PATH}")
    endforeach()
endif()

find_package(OpenCV REQUIRED)
if(OpenCV_FOUND)
    message(STATUS "OpenCV version: ${OpenCV_VERSION}")
    message(STATUS "OpenCV libraries: ${OpenCV_LIBS}")
else()
    message(FATAL_ERROR "OpenCV package not found after setting OpenCV_DIR")
endif()

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

