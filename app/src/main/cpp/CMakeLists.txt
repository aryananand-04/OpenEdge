cmake_minimum_required(VERSION 3.22.1)

project("openedge")

# C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# OpenCV Android SDK path - using local SDK at project root
# CMakeLists.txt is at app/src/main/cpp/CMakeLists.txt
# From app/src/main/cpp/ go up 4 levels to project root: ../../../../
get_filename_component(PROJECT_ROOT "${CMAKE_SOURCE_DIR}/../../../.." ABSOLUTE)
set(OPENCV_ANDROID_SDK "${PROJECT_ROOT}/OpenCV-android-sdk")

# Debug messages to verify paths
message(STATUS "CMakeLists.txt location (CMAKE_SOURCE_DIR): ${CMAKE_SOURCE_DIR}")
message(STATUS "Project root calculated: ${PROJECT_ROOT}")
message(STATUS "OpenCV SDK path: ${OPENCV_ANDROID_SDK}")

# Verify OpenCV SDK exists
if(EXISTS "${OPENCV_ANDROID_SDK}/sdk/native/jni")
    message(STATUS "OpenCV Android SDK found at: ${OPENCV_ANDROID_SDK}")
    
    # Set OpenCV include directories
    set(OpenCV_INCLUDE_DIRS
        "${OPENCV_ANDROID_SDK}/sdk/native/jni/include"
        "${OPENCV_ANDROID_SDK}/sdk/native/jni/include/opencv2"
    )
    
    include_directories(${OpenCV_INCLUDE_DIRS})
    
    # Set OpenCV library path based on ABI
    set(OpenCV_LIBS_DIR "${OPENCV_ANDROID_SDK}/sdk/native/libs/${ANDROID_ABI}")
    
    # Link OpenCV libraries - use opencv_java4 (or opencv_java3 for older versions)
    set(OpenCV_LIBS
        opencv_java4
    )
    
    set(OPENCV_FOUND TRUE)
    message(STATUS "OpenCV include dirs: ${OpenCV_INCLUDE_DIRS}")
    message(STATUS "OpenCV libs dir: ${OpenCV_LIBS_DIR}")
    message(STATUS "OpenCV Android ABIs: ${ANDROID_ABI}")
else()
    message(FATAL_ERROR "OpenCV Android SDK not found at: ${OPENCV_ANDROID_SDK}")
    message(FATAL_ERROR "Expected location: ${PROJECT_ROOT}/OpenCV-android-sdk")
    message(FATAL_ERROR "Please ensure OpenCV-android-sdk folder exists in project root")
    set(OPENCV_FOUND FALSE)
endif()

# Create native library with conditional sources
if(OPENCV_FOUND)
    add_library(
            openedge
            SHARED
            native-lib.cpp
            edge_detection.cpp
    )
    message(STATUS "OpenCV support enabled - edge detection will be available")
else()
    add_library(
            openedge
            SHARED
            native-lib.cpp
    )
    message(STATUS "Building without OpenCV - edge detection disabled")
    message(STATUS "See OPENCV_SETUP.md for instructions on configuring OpenCV")
endif()

# Find and link log library
find_library(
        log-lib
        log
)

# Link libraries
if(OPENCV_FOUND)
    # Link OpenCV from the SDK
    target_link_libraries(
            openedge
            ${log-lib}
    )
    
    # Add OpenCV library directory to linker path
    target_link_directories(openedge PRIVATE ${OpenCV_LIBS_DIR})
    target_link_libraries(openedge ${OpenCV_LIBS})
    
    # Copy OpenCV native libraries to output
    file(GLOB OPENCV_NATIVE_LIBS "${OpenCV_LIBS_DIR}/*.so")
    foreach(lib ${OPENCV_NATIVE_LIBS})
        get_filename_component(lib_name ${lib} NAME)
        add_custom_command(TARGET openedge POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E copy
            ${lib}
            ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/${lib_name}
        )
    endforeach()
else()
    target_link_libraries(
            openedge
            ${log-lib}
    )
endif()