plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.privatecompanion.eclipse"
    compileSdk = 35
    ndkVersion = "27.2.12479018"
    defaultConfig {
        applicationId = "dev.privatecompanion.eclipse"
        minSdk = 28
        targetSdk = 35
        versionCode = 8
        versionName = "0.4.4"
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild { cmake { cppFlags += listOf("-std=c++17", "-O2") } }
    }
    buildTypes {
        getByName("debug") {
            // Separate install keeps the existing companion and its private data untouched.
            applicationIdSuffix = ".preview44"
            versionNameSuffix = "-ui-test"
            resValue("string", "app_name", "Eclipse Chat Test 0.4.4")
        }
        getByName("release") {
            isMinifyEnabled = false
            resValue("string", "app_name", "Eclipse Companion")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
