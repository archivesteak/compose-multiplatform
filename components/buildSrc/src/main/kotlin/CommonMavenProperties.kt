import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
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
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
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

                //work around to fix an android publication artifact ID
                //https://youtrack.jetbrains.com/issue/KT-53520
                afterEvaluate {
                    publication.groupId = groupId
                    publication.mppArtifactId = artifactId
                    val hasPrimaryArtifact = publication.artifacts.any {
                        it.classifier.isNullOrBlank()
                    }
                    val hasJavadocArtifact = publication.artifacts.any {
                        it.classifier == "javadoc"
                    }
                    if (hasPrimaryArtifact && !hasJavadocArtifact) {
                        publication.artifact(centralJavadocJar)
                    }
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
