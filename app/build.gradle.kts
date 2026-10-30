plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.danpatten.politicalblocker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.danpatten.politicalblocker"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed so the release APK installs without a keystore. Swap for a real one before publishing.
            signingConfig = signingConfigs.getByName("debug")
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

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    testImplementation("junit:junit:4.13.2")
}
