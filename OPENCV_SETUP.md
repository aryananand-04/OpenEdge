# OpenCV Setup Instructions

## Quick Setup

1. **Download OpenCV Android SDK**
   - Go to https://opencv.org/releases/
   - Download OpenCV 4.x Android SDK (e.g., `opencv-4.9.0-android-sdk.zip`)

2. **Extract OpenCV SDK**
   ```bash
   unzip opencv-4.9.0-android-sdk.zip
   ```

3. **Configure OpenCV Path (Choose ONE method)**

   **Method 1: Environment Variable (Recommended)**
   ```bash
   export OPENCV_ANDROID_SDK=/path/to/OpenCV-android-sdk
   ```

   **Method 2: Place in Project Root**
   ```bash
   # Move or symlink OpenCV-android-sdk folder to project root
   mv OpenCV-android-sdk /path/to/OpenEdge/
   # Or create symlink
   ln -s /path/to/OpenCV-android-sdk /path/to/OpenEdge/OpenCV-android-sdk
   ```

   **Method 3: Edit CMakeLists.txt**
   - Open `app/src/main/cpp/CMakeLists.txt`
   - Find the line: `# set(OPENCV_ANDROID_SDK "/path/to/OpenCV-android-sdk")`
   - Uncomment and set your path:
   ```cmake
   set(OPENCV_ANDROID_SDK "/Users/yourname/OpenCV-android-sdk")
   ```

4. **Rebuild Project**
   ```bash
   ./gradlew clean
   ./gradlew build
   ```

## Verify Installation

After setup, you should see in build logs:
```
OpenCV Android SDK found at: /path/to/OpenCV-android-sdk
OpenCV include dirs: ...
OpenCV support enabled - edge detection will be available
```

## Troubleshooting

### Error: "OpenCV Android SDK not found!"
- Make sure OpenCV-android-sdk folder exists
- Check that path is correct (case-sensitive on Linux/Mac)
- Verify `sdk/native/jni` folder exists inside OpenCV-android-sdk

### Error: "opencv2/opencv.hpp file not found"
- OpenCV headers not found - check include paths
- Make sure OpenCV Android SDK is properly extracted
- Verify OpenCV_DIR points to `sdk/native/jni`

### Error: "libopencv_java4.so not found"
- OpenCV native libraries not found
- Check that `sdk/native/libs/arm64-v8a/` contains `.so` files
- Verify ABI matches your target (arm64-v8a or armeabi-v7a)

## Project Structure

After setup, your OpenCV SDK should be at:
```
OpenEdge/
├── OpenCV-android-sdk/          # Or wherever you placed it
│   └── sdk/
│       └── native/
│           ├── jni/
│           │   └── include/
│           │       └── opencv2/
│           └── libs/
│               ├── arm64-v8a/
│               └── armeabi-v7a/
├── app/
│   └── src/
│       └── main/
│           └── cpp/
│               └── CMakeLists.txt
└── ...
```

## Alternative: Build Without OpenCV

If you want to build without OpenCV first, the CMakeLists.txt will build successfully but edge detection will be disabled. You can add OpenCV later and rebuild.

