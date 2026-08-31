plugins {
    id("io.github.archivesteak.compose")
    kotlin("plugin.compose")
    kotlin("jvm")
}

group = "me.app"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("io.github.archivesteak.compose.components:components-resources:COMPOSE_VERSION_PLACEHOLDER")
}

