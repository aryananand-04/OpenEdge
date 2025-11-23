# OpenCV Setup for OpenEdge

## Current Status

The code structure for OpenCV edge detection is in place, but OpenCV native libraries need to be properly configured for CMake to build.

## Option 1: Using OpenCV Android SDK (Recommended)

1. Download OpenCV Android SDK from: https://opencv.org/releases/
2. Extract the SDK to a known location (e.g., `~/OpenCV-android-sdk`)
3. Update `app/src/main/cpp/CMakeLists.txt` to point to OpenCV:

```cmake
set(OpenCV_DIR "~/OpenCV-android-sdk/sdk/native/jni")
find_package(OpenCV REQUIRED)
```

4. Copy OpenCV native libraries to your project:
   - From `OpenCV-android-sdk/sdk/native/libs/` 
   - To `app/src/main/jniLibs/` (create if needed)

## Option 2: Using Prebuilt OpenCV Libraries

1. Download OpenCV prebuilt libraries for Android
2. Place `.so` files in `app/src/main/jniLibs/<ABI>/`
3. Update CMakeLists.txt to link against these libraries

## Option 3: Build OpenCV from Source

1. Follow OpenCV Android build instructions
2. Use static linking with CMake

## Note on Maven Dependency

The `opencv-android:4.9.0` Maven dependency provides Java wrappers but may not include the native libraries needed for JNI. For native code integration, you typically need the OpenCV Android SDK with native libraries.

## Current Implementation

The edge detection code is ready in:
- `app/src/main/cpp/edge_detection.cpp` - C++ Canny edge detection
- `app/src/main/java/com/openedge/processing/EdgeDetection.kt` - JNI bridge
- Integration points in `CameraManager.kt` ready for frame processing

Once OpenCV is properly configured, frames will be processed through the native edge detection pipeline.

