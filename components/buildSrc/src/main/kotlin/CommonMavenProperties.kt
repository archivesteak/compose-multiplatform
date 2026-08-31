import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure

fun Project.configureMavenPublication(
    groupId: String,
    artifactId: String,
    name: String
) {
    extensions.configure<PublishingExtension> {
        publications {
            all {
                val publication = this as MavenPublication

                //work around to fix an android publication artifact ID
                //https://youtrack.jetbrains.com/issue/KT-53520
                afterEvaluate {
                    publication.groupId = groupId
                    publication.mppArtifactId = artifactId
                }

                pom {
                    this.name.set(name)
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
                            this.name.set("archivesteak")
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
