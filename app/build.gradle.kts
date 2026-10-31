plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "net.fireassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.fireassistant"
        // Fire OS 5 (Fire 7 2015-2017, HD 8 2015-2016, HD 10 2015/2017) is API 22.
        // Fire OS 6 = API 25, Fire OS 7 = API 28, Fire OS 8 = API 30.
        minSdk = 22
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
