pluginManagement {
    plugins {
        id("org.jetbrains.kotlin.android") version "2.3.0"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
        id("com.android.application") version "8.13.2" // adjust as needed
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Manuscript"
include(":app")
