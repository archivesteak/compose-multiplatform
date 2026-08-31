rootProject.name = "bundled_kp"
include(":app")
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
        id("org.jetbrains.kotlin.multiplatform").version("KOTLIN_VERSION_PLACEHOLDER")
        id("org.jetbrains.kotlin.plugin.compose").version("KOTLIN_VERSION_PLACEHOLDER")
        id("io.github.archivesteak.compose").version("COMPOSE_GRADLE_PLUGIN_VERSION_PLACEHOLDER")
        id("com.github.gmazzo.buildconfig").version("5.3.5")
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