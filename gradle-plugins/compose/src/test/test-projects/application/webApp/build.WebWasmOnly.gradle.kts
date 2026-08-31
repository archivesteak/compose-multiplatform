plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("io.github.archivesteak.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    wasmJs {
        browser { }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.archivesteak.compose.runtime:runtime:COMPOSE_VERSION_PLACEHOLDER")
        }

        val webMain by creating { dependsOn(commonMain.get()) }
        wasmJsMain { dependsOn(webMain) }
    }
}
