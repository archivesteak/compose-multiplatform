plugins {
    id("io.github.archivesteak.compose")
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
}

kotlin {
    jvm()

    androidTarget()

    sourceSets {
        commonMain.dependencies {
            api("io.github.archivesteak.compose.runtime:runtime:COMPOSE_VERSION_PLACEHOLDER")
            api("io.github.archivesteak.compose.material:material:COMPOSE_VERSION_PLACEHOLDER")
            api("io.github.archivesteak.compose.components:components-resources:COMPOSE_VERSION_PLACEHOLDER")
        }
    }
}
android {
    namespace = "me.sample.feature"
    compileSdk = 37
}

compose.resources {
    publicResClass = true
}