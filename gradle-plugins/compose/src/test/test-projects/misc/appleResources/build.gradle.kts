plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("native.cocoapods")
    id("io.github.archivesteak.compose")
}

kotlin {
    cocoapods {
        version = "1.0"
        summary = "Some description for a Kotlin/Native module"
        homepage = "Link to a Kotlin/Native module homepage"
        pod("Base64", "1.1.2")
        framework {
            baseName = "shared"
            isStatic = true
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.archivesteak.compose.runtime:runtime:COMPOSE_VERSION_PLACEHOLDER")
                implementation("io.github.archivesteak.compose.material:material:COMPOSE_VERSION_PLACEHOLDER")
                implementation("io.github.archivesteak.compose.components:components-resources:COMPOSE_VERSION_PLACEHOLDER")
            }
        }
    }
}
