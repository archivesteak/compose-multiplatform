pluginManagement {
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
        gradlePluginPortal()
        mavenCentral()
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
        mavenCentral()
        google()
        maven("https://packages.jetbrains.team/maven/p/kt/dev")
    }
}

include(":compose")
include(":preview-rpc")
include(":jdk-version-probe")
