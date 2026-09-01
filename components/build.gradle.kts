import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform") apply false
    id("com.android.library") apply false
    id("org.jetbrains.kotlinx.binary-compatibility-validator") apply false
}

subprojects {
    version = findProperty("deploy.version")!!

    val downloadNode = providers.gradleProperty("compose.nodejs.download")
        .map(String::toBooleanStrict)
        .orElse(true)

    plugins.withType<NodeJsPlugin>().configureEach {
        extensions.configure<NodeJsEnvSpec> {
            download.set(downloadNode)
        }
    }

    plugins.withType<WasmNodeJsPlugin>().configureEach {
        extensions.configure<WasmNodeJsEnvSpec> {
            download.set(downloadNode)
        }
    }

    plugins.withId("java") {
        configureIfExists<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11

            withJavadocJar()
            withSourcesJar()
        }
    }

    tasks.withType<KotlinCompile>() {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    plugins.withId("maven-publish") {
        tasks.withType<PublishToMavenRepository>().configureEach {
            // Gradle assigns the repository after task creation. Defer inspection until the task
            // is about to execute so harmless realization (`tasks --all`, IDE import) remains safe.
            doFirst {
                check(repository.url.scheme.equals("file", ignoreCase = true)) {
                    "Remote publication is disabled while the fork publication freeze is active: " +
                        repository.url
                }
            }
        }
    }
}
