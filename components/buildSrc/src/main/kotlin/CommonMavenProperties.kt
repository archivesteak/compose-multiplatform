import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

fun Project.configureMavenPublication(
    groupId: String,
    artifactId: String,
    name: String,
    description: String
) {
    require(description.isNotBlank()) { "Maven publication description must not be blank" }

    val centralJavadocJar = tasks.register<Jar>("centralJavadocJar") {
        group = "documentation"
        this.description = "Assembles deterministic Maven Central documentation guidance"
        archiveClassifier.set("javadoc")
        destinationDirectory.set(layout.buildDirectory.dir("publications/central-javadoc"))
        duplicatesStrategy = DuplicatesStrategy.FAIL
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
        entryCompression = ZipEntryCompression.STORED
        filePermissions { unix("0644") }
        dirPermissions { unix("0755") }
        from(
            rootProject.layout.projectDirectory.file(
                "buildSrc/src/main/resources/central-javadoc/README.md"
            )
        )
    }

    extensions.configure<PublishingExtension> {
        publications {
            all {
                val publication = this as MavenPublication

                // This helper is used only by concrete library publications. Attach the
                // documentation artifact directly so configuring the build never realizes
                // Kotlin's deferred component artifacts before FinaliseDsl.
                publication.artifact(centralJavadocJar)

                //work around to fix an android publication artifact ID
                //https://youtrack.jetbrains.com/issue/KT-53520
                afterEvaluate {
                    publication.groupId = groupId
                    publication.mppArtifactId = artifactId
                }

                pom {
                    this.name.set(name)
                    this.description.set(description)
                    url.set("https://github.com/archivesteak/compose-multiplatform")
                    licenses {
                        license {
                            this.name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    developers {
                        developer {
                            id.set("archivesteak")
                            this.name.set("Jack Harrington")
                            url.set("https://github.com/archivesteak")
                        }
                    }
                    scm {
                        url.set("https://github.com/archivesteak/compose-multiplatform")
                        connection.set(
                            "scm:git:https://github.com/archivesteak/compose-multiplatform.git"
                        )
                        developerConnection.set(
                            "scm:git:ssh://git@github.com/archivesteak/compose-multiplatform.git"
                        )
                    }
                }
            }
        }
    }
}
