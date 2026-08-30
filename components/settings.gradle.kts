pluginManagement {
    repositories {
        if (extra["compose.useMavenLocal"] == "true") {
            mavenLocal {
                content {
                    includeGroupByRegex("io\\.github\\.archivesteak\\.compose(\\..+)?")
                }
            }
        }
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
    }

    plugins {
        kotlin("jvm").version(extra["kotlin.version"] as String)
        kotlin("multiplatform").version(extra["kotlin.version"] as String)
        id("org.jetbrains.kotlin.plugin.compose").version(extra["kotlin.version"] as String)
        id("io.github.archivesteak.compose").version(extra["compose.version"] as String)
        id("com.android.library").version(extra["agp.version"] as String)
        id("org.jetbrains.kotlinx.binary-compatibility-validator").version("0.17.0")
    }

    val gradlePluginDir = rootDir.resolve("../gradle-plugins")
    if (gradlePluginDir.exists()) {
        includeBuild(gradlePluginDir)
    }
}

dependencyResolutionManagement {
    repositories {
        if (extra["compose.useMavenLocal"] == "true") {
            // Fork artifacts remain isolated in the selected local repository until release.
            mavenLocal {
                content {
                    includeGroupByRegex("io\\.github\\.archivesteak(\\..+)?")
                }
            }
        }
        google()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/cmp/dev")
    }

    versionCatalogs {
        create("libs") {
            version("compose", extra["compose.version"].toString())
            version("material3", extra["compose.material3.version"].toString())
        }
    }
}

include(":SplitPane:library")
include(":SplitPane:demo")
include(":AnimatedImage:library")
include(":AnimatedImage:demo")
include(":resources:library")
include(":resources:demo:androidApp")
include(":resources:demo:desktopApp")
include(":resources:demo:shared")
include(":ui-tooling-preview:library")
include(":ui-tooling-preview:demo:desktopApp")
include(":ui-tooling-preview:demo:shared")
