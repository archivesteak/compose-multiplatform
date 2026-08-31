/*
 * Copyright 2020-2026 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.test.tests.unit

import org.gradle.api.GradleException
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import org.jetbrains.compose.ComposeBuildConfig
import org.jetbrains.compose.ComposePlugin
import org.jetbrains.compose.unsupportedUpstreamComposeCoordinates
import org.jetbrains.compose.internal.remapComposeCoordinate
import org.jetbrains.compose.internal.utils.currentTarget
import org.jetbrains.compose.test.utils.TestEnvironment
import org.jetbrains.compose.test.utils.TestProject
import org.jetbrains.compose.web.internal.ComposeUiCoordinates
import org.jetbrains.compose.web.internal.SkikoRuntimeModule
import org.jetbrains.compose.web.internal.copySkikoRuntimeAttributes
import org.jetbrains.compose.web.internal.selectComposeUiCoordinates
import org.jetbrains.compose.web.internal.skikoRuntimeModuleForComposeUiGroup
import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ForkCoordinatesTest {
    @TempDir
    lateinit var temporaryDir: File

    @Test
    fun remapsPublishedForkArtifacts() {
        mapOf(
            "org.jetbrains.compose.animation:animation" to
                "io.github.archivesteak.compose.animation:animation",
            "org.jetbrains.compose.desktop:desktop-jvm-windows-x64" to
                "io.github.archivesteak.compose.desktop:desktop-jvm-windows-x64",
            "org.jetbrains.compose.foundation:foundation" to
                "io.github.archivesteak.compose.foundation:foundation",
            "org.jetbrains.compose.material:material" to
                "io.github.archivesteak.compose.material:material",
            "org.jetbrains.compose.material3:material3" to
                "io.github.archivesteak.compose.material3:material3",
            "org.jetbrains.compose.runtime:runtime" to
                "io.github.archivesteak.compose.runtime:runtime",
            "org.jetbrains.compose.ui:ui" to
                "io.github.archivesteak.compose.ui:ui",
        ).forEach { (upstream, fork) ->
            assertEquals(fork, remapComposeCoordinate(upstream))
        }
        assertEquals(
            "io.github.archivesteak.compose.components:components-resources",
            remapComposeCoordinate("org.jetbrains.compose.components:components-resources")
        )
    }

    @Test
    fun rejectsEveryUpstreamComposeLineageButAllowsIndependentJetBrainsArtifacts() {
        assertEquals(
            listOf(
                "org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.1",
                "org.jetbrains.compose.components:components-resources:1.9.3",
                "org.jetbrains.compose.foundation:foundation:1.9.3",
                "org.jetbrains.compose.html:html-core:1.9.3",
                "org.jetbrains.compose.material:material-icons-core:1.7.3",
                "org.jetbrains.skiko:skiko:0.9.0",
            ),
            unsupportedUpstreamComposeCoordinates(
                listOf(
                    "io.github.archivesteak.compose.runtime:runtime:1.12.0-mingw",
                    "io.github.archivesteak.skiko:skiko:0.151.0-mingw",
                    "org.jetbrains.compose.annotation-internal:annotation:1.10.0",
                    "org.jetbrains.compose.collection-internal:collection:1.10.0",
                    "org.jetbrains.compose.hot-reload:hot-reload-gradle-plugin:1.2.0",
                    "org.jetbrains.compose.foundation:foundation:1.9.3",
                    "org.jetbrains.compose.material:material-icons-core:1.7.3",
                    "org.jetbrains.compose.components:components-resources:1.9.3",
                    "org.jetbrains.compose.html:html-core:1.9.3",
                    "org.jetbrains.androidx.navigationevent:navigationevent-compose:1.1.1",
                    "org.jetbrains.skiko:skiko:0.9.0",
                )
            )
        )
    }

    @Test
    fun desktopCurrentOsUsesPublishedForkCoordinateAndVersion() {
        assertEquals(
            "io.github.archivesteak.compose.desktop:desktop-jvm-${currentTarget.id}:" +
                ComposeBuildConfig.composeVersion,
            ComposePlugin.DesktopDependencies.currentOs
        )
    }

    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    @Test
    fun remapsEveryPublishedForkComponentAndLeavesRetiredArtifactsExplicit() {
        assertEquals(
            "org.jetbrains.compose.material:material-icons-core",
            remapComposeCoordinate("org.jetbrains.compose.material:material-icons-core")
        )
        assertEquals(
            "org.jetbrains.compose.material:material-icons-extended",
            remapComposeCoordinate("org.jetbrains.compose.material:material-icons-extended")
        )
        assertEquals(
            "io.github.archivesteak.compose.components:components-splitpane",
            remapComposeCoordinate("org.jetbrains.compose.components:components-splitpane")
        )
        assertEquals(
            "org.jetbrains.compose.html:html-core",
            remapComposeCoordinate("org.jetbrains.compose.html:html-core")
        )
        assertEquals(
            "io.github.archivesteak.compose.components:components-splitpane:" +
                ComposeBuildConfig.composeVersion,
            ComposePlugin.DesktopComponentsDependencies.splitPane
        )
        assertEquals(
            "io.github.archivesteak.compose.components:components-animatedimage:" +
                ComposeBuildConfig.composeVersion,
            ComposePlugin.DesktopComponentsDependencies.animatedImage
        )
        assertEquals(
            "io.github.archivesteak.compose.components:components-ui-tooling-preview:" +
                ComposeBuildConfig.composeVersion,
            ComposePlugin.CommonComponentsDependencies.uiToolingPreview
        )
    }

    @Test
    fun webRuntimePreservesTheResolvedComposeUiLineage() {
        val upstream = ComposeUiCoordinates("org.jetbrains.compose.ui", "1.12.0-beta02")
        val fork = ComposeUiCoordinates(
            "io.github.archivesteak.compose.ui",
            ComposeBuildConfig.composeVersion,
        )

        assertEquals(upstream, selectComposeUiCoordinates(listOf(upstream, upstream)))
        assertEquals(
            SkikoRuntimeModule("org.jetbrains.skiko", "skiko-js-wasm-runtime"),
            skikoRuntimeModuleForComposeUiGroup(upstream.group)
        )
        assertEquals(fork, selectComposeUiCoordinates(listOf(fork)))
        assertEquals(
            SkikoRuntimeModule("io.github.archivesteak.skiko", "skiko"),
            skikoRuntimeModuleForComposeUiGroup(fork.group)
        )
        assertFailsWith<GradleException> {
            selectComposeUiCoordinates(listOf(upstream, fork))
        }
    }

    @Test
    fun forkWebRuntimeCopiesJsAndWasmAttributesAndRequestsSkikoRuntimeUsage() {
        val project = ProjectBuilder.builder().build()
        val platformAttribute = Attribute.of("org.jetbrains.kotlin.platform.type", String::class.java)

        listOf("js", "wasm").forEach { platform ->
            val source = project.configurations.create("${platform}RuntimeSource").apply {
                attributes.attribute(platformAttribute, platform)
            }
            val target = project.configurations.create("${platform}SkikoRuntime")

            copySkikoRuntimeAttributes(project, source.attributes, target.attributes)

            assertEquals(platform, target.attributes.getAttribute(platformAttribute))
            assertEquals(
                "skiko-runtime",
                target.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name
            )
        }
    }

    @Test
    fun legacyPluginIdUsesTheForkImplementationFromTheSameJar() {
        val properties = Properties()
        val descriptor = checkNotNull(
            javaClass.classLoader.getResourceAsStream(
                "META-INF/gradle-plugins/org.jetbrains.compose.properties"
            )
        )
        descriptor.use { properties.load(it) }

        assertEquals(
            "org.jetbrains.compose.ComposePlugin",
            properties.getProperty("implementation-class")
        )
    }

    @Test
    fun testFixturesUseThePublishedForkPluginIdDirectly() {
        TestProject(
            name = "application/defaultArgs",
            testEnvironment = TestEnvironment(
                workingDir = temporaryDir,
                kotlinVersion = "2.2.20",
                gradleVersion = "9.5.0",
                agpVersion = "9.0.0",
                composeGradlePluginVersion = ComposeBuildConfig.composeGradlePluginVersion,
                composeVersion = ComposeBuildConfig.composeVersion,
                useGradleConfigurationCache = false,
            )
        )

        val fixtureSettings = temporaryDir.resolve("settings.gradle").readText()
        assertEquals(
            true,
            fixtureSettings.contains("id 'io.github.archivesteak.compose'"),
        )
        assertEquals(false, temporaryDir.resolve(".compose-fork-plugin-resolution.init.gradle").exists())
    }
}
