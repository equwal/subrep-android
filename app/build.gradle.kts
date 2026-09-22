plugins {
    alias(libs.plugins.android.application)
}

// Release signing comes from the environment, so the key never lives in the repository.
val keystorePath = providers.environmentVariable("SUBREP_KEYSTORE_FILE").orNull
val keystorePassword = providers.environmentVariable("SUBREP_KEYSTORE_PASSWORD").orNull
// Not named keyAlias: inside signingConfigs, that name is the property of the config itself.
val signingAlias = providers.environmentVariable("SUBREP_KEY_ALIAS").orNull ?: "subrep"
val signingReady = !keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() &&
    file(keystorePath).isFile

android {
    namespace = "com.honjimaku.subrep"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.honjimaku.subrep"
        // The sound of other apps can be captured from Android 10 (API 29). Older devices get the microphone.
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0"

        // Each phone worth running Whisper on is 64-bit ARM; the speech library is built for
        // ARMv8.2 (see src/main/cpp/CMakeLists.txt).
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_STL=c++_static") }
        }
    }

    if (signingReady) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = signingAlias
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = false
            if (signingReady) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
}

dependencies {
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.json)
}

tasks.withType<Test> {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
