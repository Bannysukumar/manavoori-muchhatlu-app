plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.manavoori.muchhatlu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.manavoori.muchhatlu"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Handle externalOverride signing config
        // If the keystore file doesn't exist, use default debug keystore to avoid validation errors
        val customKeystorePath = file("C:\\Users\\GOPI HARIKRISHNA\\Downloads\\app key")
        val keystoreFile = if (customKeystorePath.exists()) {
            customKeystorePath
        } else {
            // Use default debug keystore location as fallback
            file("${System.getProperty("user.home")}\\.android\\debug.keystore")
        }
        
        create("externalOverride") {
            storeFile = keystoreFile
            // Use default debug credentials if custom keystore doesn't exist
            if (customKeystorePath.exists()) {
                storePassword = project.findProperty("KEYSTORE_PASSWORD") as String? ?: "android"
                keyAlias = project.findProperty("KEY_ALIAS") as String? ?: "androiddebugkey"
                keyPassword = project.findProperty("KEY_PASSWORD") as String? ?: "android"
            } else {
                // Default debug keystore credentials
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            // Use externalOverride config (which falls back to debug keystore if custom one doesn't exist)
            signingConfig = signingConfigs.getByName("externalOverride")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only use externalOverride for release if custom keystore exists
            val customKeystorePath = file("C:\\Users\\GOPI HARIKRISHNA\\Downloads\\app key")
            if (customKeystorePath.exists()) {
                signingConfig = signingConfigs.getByName("externalOverride")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.glide)
    implementation(libs.cardview)
    implementation(libs.swiperefresh)
    implementation(libs.preference)
    implementation(libs.core.splashscreen)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}