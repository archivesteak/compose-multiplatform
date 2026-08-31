plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("io.github.archivesteak.compose")
}

group = "app.group"

kotlin {
    jvm("desktop")

    iosArm64()
    iosSimulatorArm64()

    macosArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.archivesteak.compose.runtime:runtime:COMPOSE_VERSION_PLACEHOLDER")
                implementation("io.github.archivesteak.compose.material:material:COMPOSE_VERSION_PLACEHOLDER")
                implementation("io.github.archivesteak.compose.components:components-resources:COMPOSE_VERSION_PLACEHOLDER")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.github.archivesteak.compose.ui:ui-test:COMPOSE_VERSION_PLACEHOLDER")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}
