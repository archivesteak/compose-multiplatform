rootProject.name = "mingwNativeResources"

pluginManagement {
    val forkRepositoryPath = System.getProperty("maven.repo.local")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error("Pass -Dmaven.repo.local=<absolute isolated repository path>")
    val forkRepository = java.io.File(forkRepositoryPath).canonicalFile
    check(forkRepository.isAbsolute && forkRepository.isDirectory) {
        "The isolated fork repository must be an existing absolute directory: $forkRepositoryPath"
    }

    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "isolatedForkRepository"
                    url = uri(forkRepository)
                }
            }
            filter { includeGroupByRegex("io\\.github\\.archivesteak\\.compose(\\..*)?") }
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
    val forkRepositoryPath = System.getProperty("maven.repo.local")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: error("Pass -Dmaven.repo.local=<absolute isolated repository path>")
    val forkRepository = java.io.File(forkRepositoryPath).canonicalFile
    check(forkRepository.isAbsolute && forkRepository.isDirectory) {
        "The isolated fork repository must be an existing absolute directory: $forkRepositoryPath"
    }

    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "isolatedForkRepository"
                    url = uri(forkRepository)
                }
            }
            filter { includeGroupByRegex("io\\.github\\.archivesteak(\\..*)?") }
        }
        mavenCentral()
    }
}
