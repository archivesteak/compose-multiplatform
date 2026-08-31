plugins {
    id("io.github.archivesteak.compose")
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm()

    android {
        namespace = "me.sample.feature"
        compileSdk = 37
        minSdk = 23
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            api("io.github.archivesteak.compose.runtime:runtime:COMPOSE_VERSION_PLACEHOLDER")
            api("io.github.archivesteak.compose.material:material:COMPOSE_VERSION_PLACEHOLDER")
            api("io.github.archivesteak.compose.components:components-resources:COMPOSE_VERSION_PLACEHOLDER")
        }
    }
}

//https://youtrack.jetbrains.com/issue/CMP-8325
compose.desktop {
    application { }
}

compose.resources {
    publicResClass = true
}