plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.openedge"
    compileSdk = 36

    // Specify NDK version
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.openedge"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Native build configuration
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared")
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Configure CMake
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    
    // Exclude transitive coordinatorlayout from material to avoid duplicate class error
    // and add it explicitly with a single version
    implementation(libs.material) {
        exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
    }
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    
    // OpenCV Android SDK - using local SDK at project root
    // Native libraries linked via CMakeLists.txt
    // No Java dependency needed - using OpenCV C++ API through JNI only
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}