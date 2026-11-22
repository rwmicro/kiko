rootProject.name = "kiko"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // LiveKit s'appuie sur une version d'AudioSwitch publiée seulement sur JitPack.
        maven("https://jitpack.io") {
            mavenContent { includeGroup("com.github.davidliu") }
        }
    }
}

include(":composeApp")
include(":androidApp")
