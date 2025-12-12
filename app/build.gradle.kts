plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}



android {
    namespace = "com.blackbriar.musicsupervisor"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.blackbriar.musicsupervisor"
        minSdk = 24
        targetSdk = 36
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            // Set JVM target for Kotlin compilation
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "2.3.0" // match Kotlin plugin version
    }
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("androidx.appcompat:appcompat:1.6.1")

//    implementation("androidx.core:core-ktx:1.12.0")
//    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
//    implementation("androidx.activity:activity-compose:1.8.1")
//
//    implementation(platform("androidx.compose:compose-bom:2024.04.00")) // Use the BOM for version alignment
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.5") // Check for the latest stable version

//    implementation(platform("com.google.firebase:firebase-bom:34.5.0"))
    implementation(platform(libs.firebase.bom))

    implementation("com.google.firebase:firebase-common")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    implementation("com.google.android.material:material:1.13.0")


    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation(libs.coil.kt)

//    implementation("io.coil-kt:coil:3.2.0")
    implementation("io.coil-kt.coil3:coil-core-android:3.3.0")
    implementation("io.coil-kt.coil3:coil-compose-core-android:3.3.0")



    // for material
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}