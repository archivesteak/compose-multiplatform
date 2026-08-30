plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("io.github.archivesteak.compose")
}

group = "app.group"

val windowsTarget = kotlin.mingwX64 {
    binaries.executable {
        entryPoint = "main"
    }
}

kotlin.sourceSets.commonMain.dependencies {
    implementation(compose.runtime)
    implementation(compose.components.resources)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "mingwresources.generated.resources"
    generateResClass = always
}

compose.desktop {
    nativeApplication {
        targets(windowsTarget)
        distributions {
            packageName = "Test Resources"
        }
    }
}
