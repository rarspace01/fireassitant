plugins {
    id("com.android.application")
}

android {
    namespace = "net.fireassistant"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.fireassistant"
        // Fire OS 6 = API 25, Fire OS 7 = API 28, Fire OS 8 = API 30. Fire OS 5 (API 22,
        // 2015-2017 tablets) is dropped: androidx.core 1.19 needs API 23.
        minSdk = 23
        // Fire OS tops out at Android 11. Targeting 30 avoids the Android 13/14
        // notification-permission and foreground-service-type requirements.
        targetSdk = 30
        // CI sets these from the release tag (v1.2.3) and run number.
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0"
    }

    // Release signing comes from CI secrets; without them the release APK is signed with the
    // debug key so it is still installable.
    val keystorePath = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    lint {
        // targetSdk 30 is deliberate (Fire OS tops out at Android 11); this app is sideloaded, not on Play.
        disable += "ExpiredTargetSdkVersion"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("androidx.core:core:1.19.1")
}
