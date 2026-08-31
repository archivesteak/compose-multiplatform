rootProject.name = "Multiplatform-App"

pluginManagement {
    repositories {
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeGroupByRegex("io\\.github\\.archivesteak(\\..*)?") }
        }
        gradlePluginPortal()
        google()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
    }
    plugins {
        id("com.android.kotlin.multiplatform.library").version("AGP_VERSION_PLACEHOLDER")
        id("com.android.application").version("AGP_VERSION_PLACEHOLDER")
        id("org.jetbrains.kotlin.multiplatform").version("KOTLIN_VERSION_PLACEHOLDER")
        id("org.jetbrains.kotlin.android").version("KOTLIN_VERSION_PLACEHOLDER")
        id("org.jetbrains.kotlin.jvm").version("KOTLIN_VERSION_PLACEHOLDER")
        id("org.jetbrains.kotlin.plugin.compose").version("KOTLIN_VERSION_PLACEHOLDER")
        id("io.github.archivesteak.compose").version("COMPOSE_GRADLE_PLUGIN_VERSION_PLACEHOLDER")
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        mavenCentral()
        gradlePluginPortal()
        google()
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeGroupByRegex("io\\.github\\.archivesteak(\\..*)?") }
        }
    }
}

include(":sharedUI")
include(":androidApp")
include(":desktopApp")
include(":webApp")

