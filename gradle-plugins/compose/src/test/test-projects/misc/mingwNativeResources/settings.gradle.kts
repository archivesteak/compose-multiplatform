rootProject.name = "mingwNativeResources"

pluginManagement {
    repositories {
        mavenLocal {
            content {
                includeGroupByRegex("io\\.github\\.archivesteak\\.compose(\\..+)?")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.multiplatform").version("KOTLIN_VERSION_PLACEHOLDER")
        id("org.jetbrains.kotlin.plugin.compose").version("KOTLIN_VERSION_PLACEHOLDER")
        id("io.github.archivesteak.compose").version("COMPOSE_GRADLE_PLUGIN_VERSION_PLACEHOLDER")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal {
            content {
                includeGroupByRegex("io\\.github\\.archivesteak(\\..+)?")
            }
        }
        mavenCentral()
    }
}
