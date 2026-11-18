plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rav.asistent"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rav.asistent"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    // Stalni potpis: svaka nova verzija se instalira preko stare bez brisanja
    signingConfigs {
        create("stalni") {
            storeFile = file("asistent.keystore")
            storePassword = "asistent123"
            keyAlias = "asistent"
            keyPassword = "asistent123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stalni")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
