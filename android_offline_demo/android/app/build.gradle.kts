plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {

    // BEGIN OPENCV / YOLO NATIVE LIBRARY FIX
    packaging {
        jniLibs {
            pickFirsts.add("**/libc++_shared.so")
        }
    }
    // END OPENCV / YOLO NATIVE LIBRARY FIX


    namespace = "com.example.bangbangblind_demo"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.example.bangbangblind_demo"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}


// BEGIN CNN LITERT DEPENDENCY FIX
// CPU CNN test: remove the older GPU dependency that brings litert-api 1.4.0.
configurations.configureEach {
    exclude(
        group = "com.google.ai.edge.litert",
        module = "litert-gpu"
    )
}
// END CNN LITERT DEPENDENCY FIX


// BEGIN CNN NATIVE LITERT
dependencies {
    implementation("com.google.ai.edge.litert:litert:2.1.5")
}
// END CNN NATIVE LITERT


// BEGIN SEGMENTER V4 OPENCV
dependencies {
    implementation("org.opencv:opencv:4.11.0")
}
// END SEGMENTER V4 OPENCV
