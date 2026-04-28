plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Required for Firebase
    id("com.google.gms.google-services")
}

android {
    namespace = "com.nimmaguru.app"
    compileSdk = 34 // Use 34 to support latest Compose features

    defaultConfig {
        applicationId = "com.nimmaguru.app" // Restored to match your Firebase google-services.json
        minSdk = 24  // Recommended minimum for modern Firebase/Compose
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }



    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Appcompat added here to resolve your Theme error in Manifest!
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // Compose Activity
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // FIX: Added Material Icons Extended to fix (Language, School, Visibility errors)
    implementation("androidx.compose.material:material-icons-extended")

    // Compose Navigation (Required for NavHost!)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))

    // Auth & Firestore Dependencies
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
}
