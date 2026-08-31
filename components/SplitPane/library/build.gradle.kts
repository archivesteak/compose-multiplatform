plugins {
    kotlin("multiplatform")
    id("io.github.archivesteak.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

kotlin {
    jvm("desktop")

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
            }
        }

        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
        }
    }
}

configureMavenPublication(
    groupId = "io.github.archivesteak.compose.components",
    artifactId = "components-splitpane",
    name = "SplitPane for Compose Multiplatform",
    description = "Resizable split-pane layouts for Compose Multiplatform."
)
