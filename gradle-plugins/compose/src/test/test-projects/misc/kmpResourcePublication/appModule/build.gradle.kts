plugins {
    id("io.github.archivesteak.compose")
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    android {
        compileSdk = 37
        namespace = "me.sample.app"
        minSdk = 23
        androidResources.enable = true
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    js { browser() }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.archivesteak.compose.runtime:runtime:COMPOSE_VERSION_PLACEHOLDER")
            implementation("io.github.archivesteak.compose.material3:material3:COMPOSE_MATERIAL3_VERSION_PLACEHOLDER")
            implementation("io.github.archivesteak.compose.components:components-resources:COMPOSE_VERSION_PLACEHOLDER")
            implementation("me.sample.library:cmplib:1.0")
            implementation(project(":featureModule"))
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.github.archivesteak.compose.ui:ui-test:COMPOSE_VERSION_PLACEHOLDER")
        }
    }
}
