rootProject.name = "newAgpResources"
include(":featureModule")
include(":appModule")
pluginManagement {
    repositories {
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeGroupByRegex("io\\.github\\.archivesteak(\\..*)?") }
        }
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        maven("https://redirector.kotlinlang.org/maven/dev/")
    }
    plugins {
        id("org.jetbrains.kotlin.multiplatform").version("KOTLIN_VERSION_PLACEHOLDER")
        id("org.jetbrains.kotlin.plugin.compose").version("KOTLIN_VERSION_PLACEHOLDER")
        id("io.github.archivesteak.compose").version("COMPOSE_GRADLE_PLUGIN_VERSION_PLACEHOLDER")
        id("com.android.kotlin.multiplatform.library").version("AGP_VERSION_PLACEHOLDER")
        id("com.android.application").version("AGP_VERSION_PLACEHOLDER")
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
        maven("https://redirector.kotlinlang.org/maven/dev/")
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeGroupByRegex("io\\.github\\.archivesteak(\\..*)?") }
        }
    }
}