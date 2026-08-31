import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow.jar) apply false
    alias(libs.plugins.download) apply false
}

subprojects {
    group = BuildProperties.group
    version = BuildProperties.deployVersion(project)

    plugins.withId("java") {
        configureIfExists<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11

            withJavadocJar()
            withSourcesJar()
        }
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        // Java and Kotlin both contribute a source archive once withSourcesJar() is enabled.
        // Publish Kotlin's complete source archive exactly once.
        configurations.named("sourcesElements") {
            outgoing.artifacts.clear()
            outgoing.artifact(tasks.named("kotlinSourcesJar"))
        }

        tasks.withType(KotlinJvmCompile::class).configureEach {
            compilerOptions {
                // must be set to a language version of the kotlin compiler & runtime,
                // which is bundled to the oldest supported Gradle
                // https://docs.gradle.org/current/userguide/compatibility.html#kotlin
                languageVersion.set(KotlinVersion.KOTLIN_2_0)
                apiVersion.set(KotlinVersion.KOTLIN_2_0)
                jvmTarget.set(JvmTarget.JVM_11)
            }
        }
    }

    plugins.withId("maven-publish") {
        configureIfExists<PublishingExtension> {
            repositories {
                maven {
                    name = "LocalDir"
                    url = rootProject.buildDir.resolve("repo").toURI()
                }
            }
        }

        tasks.withType<PublishToMavenRepository>().configureEach {
            // Gradle assigns the repository after creating the publication task. Inspecting it
            // while the task is merely being realized (for example by `tasks --all`) can observe
            // the temporary null state; the execution guard still runs before every publish action.
            doFirst {
                check(repository.url.scheme.equals("file", ignoreCase = true)) {
                    "Remote publication is disabled while the fork publication freeze is active: " +
                        repository.url
                }
            }
        }
    }

    afterEvaluate {
        val publicationConfig = mavenPublicationConfig
        val gradlePluginConfig = gradlePluginConfig

        if (publicationConfig != null) {
            if (gradlePluginConfig != null) {
                // pluginMaven is a default publication created by java-gradle-plugin
                // https://github.com/gradle/gradle/issues/10384
                configureMavenPublication("pluginMaven", publicationConfig)
                configureGradlePlugin(publicationConfig, gradlePluginConfig)
                configurePluginMarkerPublications(publicationConfig)
            } else {
                configureMavenPublication("maven", publicationConfig) {
                    from(components["java"])
                }
            }
        }
    }

    tasks.withType<AbstractTestTask>().configureEach {
        outputs.upToDateWhen { false }
    }
}

fun Project.configureMavenPublication(
    publicationName: String,
    config: MavenPublicationConfigExtension,
    customize: MavenPublication.() -> Unit = {}
) {
    // maven publication for plugin
    configureIfExists<PublishingExtension> {
        publications.create<MavenPublication>(publicationName) {
            artifactId = config.artifactId
            pom {
                name.set(config.displayName)
                description.set(config.description)
                url.set(BuildProperties.website)
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("archivesteak")
                        name.set("archivesteak")
                        url.set("https://github.com/archivesteak")
                    }
                }
                scm {
                    url.set(BuildProperties.vcs)
                    connection.set("scm:git:${BuildProperties.vcs}.git")
                    developerConnection.set("scm:git:ssh://git@github.com/archivesteak/compose-multiplatform.git")
                }
            }

            customize()
        }
    }
}

fun Project.configurePluginMarkerPublications(config: MavenPublicationConfigExtension) {
    configureIfExists<PublishingExtension> {
        publications.withType<MavenPublication>()
            .matching { publication -> publication.name.endsWith("PluginMarkerMaven") }
            .configureEach {
                pom {
                    name.set(config.displayName)
                    description.set(config.description)
                    url.set(BuildProperties.website)
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("archivesteak")
                            name.set("archivesteak")
                            url.set("https://github.com/archivesteak")
                        }
                    }
                    scm {
                        url.set(BuildProperties.vcs)
                        connection.set("scm:git:${BuildProperties.vcs}.git")
                        developerConnection.set(
                            "scm:git:ssh://git@github.com/archivesteak/compose-multiplatform.git"
                        )
                    }
                }
            }
    }
}

@Suppress("UnstableApiUsage")
fun Project.configureGradlePlugin(
    publicationConfig: MavenPublicationConfigExtension,
    gradlePluginConfig: GradlePluginConfigExtension
) {
    // gradle plugin definition (relates to gradlePlugin extension block from java-gradle-plugin)
    configureIfExists<GradlePluginDevelopmentExtension> {
        vcsUrl.set(BuildProperties.vcs)
        website.set(BuildProperties.website)
        description = publicationConfig.description

        plugins {
            create("gradlePlugin") {
                id = gradlePluginConfig.pluginId
                displayName = publicationConfig.displayName
                description = publicationConfig.description
                implementationClass = gradlePluginConfig.implementationClass
                version = project.version
            }
        }
    }
}

tasks.register("publishToMavenLocal") {
    val publishToMavenLocal = this
    for (subproject in subprojects) {
        subproject.plugins.withId("maven-publish") {
            publishToMavenLocal.dependsOn("${subproject.path}:publishToMavenLocal")
        }
    }
}
