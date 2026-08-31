plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.github.archivesteak.compose")
    id("co.touchlab.kmmbridge").version("0.5.7")
}

kotlin {
    js {
        browser { }
        binaries.executable()
    }

    wasmJs {
        browser { }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.archivesteak.compose.runtime:runtime:COMPOSE_VERSION_PLACEHOLDER")
        }

        val webMain by creating { dependsOn(commonMain.get()) }
        jsMain { dependsOn(webMain) }
        wasmJsMain { dependsOn(webMain) }
    }
}
