plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("io.github.archivesteak.compose")
}

group = "app.group"

kotlin {
    jvm("desktop")

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
