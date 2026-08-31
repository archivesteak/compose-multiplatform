/*
 * Copyright 2020-2026 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.test.tests.unit

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ForkRepositoryIsolationTest {
    @TempDir
    lateinit var temporaryDir: File

    @Test
    fun forkGroupCannotFallBackToAnotherRepository() {
        val isolatedRepository = temporaryDir.resolve("isolated-m2").apply { mkdirs() }
        val fallbackRepository = temporaryDir.resolve("fallback-m2").apply { mkdirs() }
        writeProbeModule(fallbackRepository)

        val projectDir = temporaryDir.resolve("project").apply { mkdirs() }
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "fork-repository-isolation"
            dependencyResolutionManagement {
                repositories {
                    exclusiveContent {
                        forRepository {
                            maven { url = uri("${isolatedRepository.invariantSeparatorsPath}") }
                        }
                        filter { includeGroupByRegex("io\\.github\\.archivesteak(\\..*)?") }
                    }
                    maven { url = uri("${fallbackRepository.invariantSeparatorsPath}") }
                }
            }
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            val probe by configurations.creating { isCanBeResolved = true }
            dependencies { probe("io.github.archivesteak.probe:artifact:1.0") }
            tasks.register("resolveProbe") {
                doLast { println("PROBE=" + probe.singleFile.name) }
            }
            """.trimIndent(),
        )

        val failed = runner(projectDir).withArguments("resolveProbe", "--stacktrace").buildAndFail()
        assertContains(failed.output, "Could not find io.github.archivesteak.probe:artifact:1.0")
        assertFalse(failed.output.contains("fallback-m2"))

        writeProbeModule(isolatedRepository)
        val succeeded = runner(projectDir).withArguments("resolveProbe", "--stacktrace").build()
        assertContains(succeeded.output, "PROBE=artifact-1.0.jar")
    }

    private fun runner(projectDir: File): GradleRunner = GradleRunner.create()
        .withGradleVersion("9.5.0")
        .withProjectDir(projectDir)

    private fun writeProbeModule(repository: File) {
        val moduleDir = repository.resolve("io/github/archivesteak/probe/artifact/1.0")
            .apply { mkdirs() }
        moduleDir.resolve("artifact-1.0.pom").writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>io.github.archivesteak.probe</groupId>
              <artifactId>artifact</artifactId>
              <version>1.0</version>
            </project>
            """.trimIndent(),
        )
        JarOutputStream(
            moduleDir.resolve("artifact-1.0.jar").outputStream(),
            Manifest().apply { mainAttributes.putValue("Manifest-Version", "1.0") },
        ).use { }
    }
}
