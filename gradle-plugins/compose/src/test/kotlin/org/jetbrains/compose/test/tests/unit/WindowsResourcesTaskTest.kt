/*
 * Copyright 2020-2026 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.test.tests.unit

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import org.jetbrains.compose.ComposeBuildConfig
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.test.utils.TestProperties
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsResourcesTaskTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun syncsApplicationAndTestExecutableResources() {
        val pluginJar = File(ComposePlugin::class.java.protectionDomain.codeSource.location.toURI())
        check(pluginJar.isFile) { "The plugin test classpath must start with the shadow JAR: $pluginJar" }

        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "windows-resource-staging"
            """.trimIndent()
        )
        projectDir.resolve("build.gradle").writeText(
            """
            buildscript {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
                dependencies {
                    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${TestProperties.kotlinVersion}"
                    classpath "org.jetbrains.kotlin:compose-compiler-gradle-plugin:${TestProperties.kotlinVersion}"
                    classpath "org.jetbrains.compose.hot-reload:hot-reload-gradle-plugin:${ComposeBuildConfig.composeHotReloadVersion}"
                    classpath files("${pluginJar.invariantSeparatorsPath}")
                }
            }

            apply plugin: "org.jetbrains.kotlin.multiplatform"
            apply plugin: "org.jetbrains.kotlin.plugin.compose"
            apply plugin: "io.github.archivesteak.compose"

            repositories {
                mavenCentral()
            }

            kotlin {
                mingwX64 {
                    binaries.executable { entryPoint = "main" }
                }
            }

            tasks.register("verifyWindowsResourceTaskWiring") {
                doLast {
                    def verify = { linkTaskName, syncTaskName ->
                        def syncTask = tasks.getByName(syncTaskName)
                        assert syncTask instanceof org.gradle.api.tasks.Sync:
                            "${'$'}syncTaskName must use Sync semantics"
                        def linkTask = tasks.getByName(linkTaskName)
                        assert linkTask.taskDependencies.getDependencies(linkTask).contains(syncTask):
                            "${'$'}linkTaskName must depend on ${'$'}syncTaskName"
                    }

                    verify(
                        "linkDebugExecutableMingwX64",
                        "syncDebugExecutableMingwX64ComposeResources",
                    )
                    verify(
                        "linkDebugTestMingwX64",
                        "syncDebugTestMingwX64ComposeResources",
                    )
                    println("WINDOWS_RESOURCE_TASK_WIRING_OK")
                }
            }
            """.trimIndent()
        )

        val mainResource = projectDir.resolve(
            "src/commonMain/composeResources/files/sync-lifecycle.txt"
        ).apply {
            parentFile.mkdirs()
            writeText("first")
        }
        projectDir.resolve("src/mingwX64Test/composeResources/files/test-only.txt").apply {
            parentFile.mkdirs()
            writeText("test")
        }

        val wiring = runner("verifyWindowsResourceTaskWiring").build()
        assertTrue("WINDOWS_RESOURCE_TASK_WIRING_OK" in wiring.output, wiring.output)

        runner("syncDebugExecutableMingwX64ComposeResources").build()
        assertEquals(
            setOf("first"),
            stagedResourceContents("debugExecutable", "sync-lifecycle.txt"),
        )

        mainResource.writeText("second")
        runner("syncDebugExecutableMingwX64ComposeResources").build()
        assertEquals(
            setOf("second"),
            stagedResourceContents("debugExecutable", "sync-lifecycle.txt"),
        )

        check(mainResource.delete())
        runner("syncDebugExecutableMingwX64ComposeResources").build()
        assertFalse(stagedResources("debugExecutable", "sync-lifecycle.txt").any())

        runner("syncDebugTestMingwX64ComposeResources").build()
        assertEquals(setOf("test"), stagedResourceContents("debugTest", "test-only.txt"))
    }

    private fun runner(vararg tasks: String): GradleRunner = GradleRunner.create()
        .withGradleVersion("9.5.0")
        .withProjectDir(projectDir)
        .withArguments(*tasks, "--stacktrace", "--no-configuration-cache")

    private fun stagedResourceContents(binaryDirectory: String, fileName: String): Set<String> {
        val resources = stagedResources(binaryDirectory, fileName).toList()
        assertTrue(resources.isNotEmpty(), "No staged '$fileName' resource was found")
        return resources.mapTo(mutableSetOf(), File::readText)
    }

    private fun stagedResources(binaryDirectory: String, fileName: String): Sequence<File> {
        val resourcesRoot = projectDir.resolve(
            "build/bin/mingwX64/$binaryDirectory/compose-resources"
        )
        return if (resourcesRoot.isDirectory) {
            resourcesRoot.walkTopDown().filter { file -> file.isFile && file.name == fileName }
        } else {
            emptySequence()
        }
    }
}
