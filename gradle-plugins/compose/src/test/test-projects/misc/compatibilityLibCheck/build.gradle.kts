plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.archivesteak.compose")
}

kotlin {
    android {
        namespace = "org.company.app"
        compileSdk = 37
        minSdk = 23
        androidResources.enable = true
    }

    jvm()

    js { browser() }
    wasmJs { browser() }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("io.github.archivesteak.compose.runtime:runtime:COMPOSE_VERSION_PLACEHOLDER")
            api("io.github.archivesteak.compose.ui:ui:COMPOSE_VERSION_PLACEHOLDER")
            api("io.github.archivesteak.compose.foundation:foundation:COMPOSE_VERSION_PLACEHOLDER")
        }
    }
}
