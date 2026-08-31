pluginManagement {
    mapOf(
        "kotlin.version" to "2.4.10",
        "compose.version" to "1.12.0-beta02-mingw",
        "compose.material3.version" to "1.12.0-alpha03-mingw",
        "deploy.version" to "1.12.0-beta02-mingw",
    ).forEach { (property, expected) ->
        check(extra[property].toString() == expected) {
            "The fork requires $property=$expected, but found ${extra[property]}"
        }
    }

    val explicitForkRepositoryPath = System.getProperty("maven.repo.local")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error(
            "This build requires an explicit isolated fork repository. " +
                "Pass -Dmaven.repo.local=<absolute repository path>; " +
                "ambient ~/.m2 resolution is disabled.",
        )
    val suppliedForkRepository = java.io.File(explicitForkRepositoryPath)
    check(suppliedForkRepository.isAbsolute) {
        "The isolated fork repository path must be absolute: $explicitForkRepositoryPath"
    }
    val explicitForkRepository = suppliedForkRepository.canonicalFile
    val ambientMavenDirectory = file(System.getProperty("user.home"))
        .resolve(".m2")
        .canonicalFile
    check(explicitForkRepository.isDirectory) {
        "The isolated fork repository must be an existing absolute directory: " +
            explicitForkRepository.path
    }
    check(!explicitForkRepository.toPath().startsWith(ambientMavenDirectory.toPath())) {
        "The isolated fork repository must not be inside the ambient Maven directory " +
            "${ambientMavenDirectory.path}: ${explicitForkRepository.path}"
    }

    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "isolatedForkRepository"
                    url = uri(explicitForkRepository)
                }
            }
            filter {
                includeGroupByRegex("io\\.github\\.archivesteak\\.compose(\\..*)?")
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

}

check(!gradle.startParameter.isBuildScan) {
    "Build scans are disabled while the fork publication freeze is active."
}

dependencyResolutionManagement {
    val explicitForkRepositoryPath = System.getProperty("maven.repo.local")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error(
            "This build requires an explicit isolated fork repository. " +
                "Pass -Dmaven.repo.local=<absolute repository path>; " +
                "ambient ~/.m2 resolution is disabled.",
        )
    val suppliedForkRepository = java.io.File(explicitForkRepositoryPath)
    check(suppliedForkRepository.isAbsolute) {
        "The isolated fork repository path must be absolute: $explicitForkRepositoryPath"
    }
    val explicitForkRepository = suppliedForkRepository.canonicalFile
    val ambientMavenDirectory = file(System.getProperty("user.home"))
        .resolve(".m2")
        .canonicalFile
    check(explicitForkRepository.isDirectory) {
        "The isolated fork repository must be an existing absolute directory: " +
            explicitForkRepository.path
    }
    check(!explicitForkRepository.toPath().startsWith(ambientMavenDirectory.toPath())) {
        "The isolated fork repository must not be inside the ambient Maven directory " +
            "${ambientMavenDirectory.path}: ${explicitForkRepository.path}"
    }

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Exclusive ownership is intentional: an incomplete local fork must fail instead of
        // silently mixing with a public artifact that happens to share a version.
        exclusiveContent {
            forRepository {
                maven {
                    name = "isolatedForkRepository"
                    url = uri(explicitForkRepository)
                }
            }
            filter {
                includeGroupByRegex("io\\.github\\.archivesteak(\\..*)?")
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
