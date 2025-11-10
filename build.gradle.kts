

plugins {
    // FIX: Using direct ID and version to bypass version catalog alias issues.
    id("com.google.gms.google-services") version "4.4.1" apply false

    // Core Android Gradle Plugin (AGP) version: 8.13.0
    id("com.android.application") version "8.13.0" apply false

    // FIX: Updated the Kotlin plugin version to 2.0.21 to resolve the conflict.
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}

