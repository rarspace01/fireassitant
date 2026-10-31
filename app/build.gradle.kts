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
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
