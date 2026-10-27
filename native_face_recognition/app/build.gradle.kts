plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.facerecognition"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {

        ndk {
            abiFilters += listOf(
                "x86",
                "arm64-v8a",
                "armeabi-v7a"
            )
        }
    }

    defaultConfig {
        applicationId = "com.example.facerecognition"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
val cameraxVersion = "1.3.0"
dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    // MediaPipe Face Detection
    implementation("com.google.mediapipe:tasks-vision:0.10.8")
    // TensorFlow Lite
    // 組員B：用於載入 MobileFaceNet 模型並提取人臉特徵向量
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
}