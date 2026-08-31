plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("io.github.archivesteak.compose")
    id("com.github.gmazzo.buildconfig")
}

group = "app.group"

kotlin {
    jvm()

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

buildConfig {
    buildConfigField(String::class.java, "str", "")
}
