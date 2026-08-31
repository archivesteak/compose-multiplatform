/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

@file:Suppress("unused")

package org.jetbrains.compose

import groovy.lang.Closure
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.compose.desktop.application.internal.configureDesktop
import org.jetbrains.compose.desktop.preview.internal.initializePreview
import org.jetbrains.compose.experimental.internal.configureExperimentalTargetsFlagsCheck
import org.jetbrains.compose.internal.remapComposeCoordinate
import org.jetbrains.compose.internal.KOTLIN_MPP_PLUGIN_ID
import org.jetbrains.compose.internal.mppExt
import org.jetbrains.compose.internal.utils.currentTarget
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.compose.resources.configureComposeResources
import org.jetbrains.compose.web.WebExtension
import org.jetbrains.compose.web.internal.configureWeb
import org.jetbrains.compose.web.tasks.configureWebCompatibility
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

internal val composeVersion get() = ComposeBuildConfig.composeVersion
internal val composeUpstreamVersion get() = ComposeBuildConfig.composeUpstreamVersion
internal val composeMaterial3Version get() = ComposeBuildConfig.composeMaterial3Version

abstract class ComposePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val composeExtension = project.extensions.create("compose", ComposeExtension::class.java, project)
        val desktopExtension = composeExtension.extensions.create("desktop", DesktopExtension::class.java)
        val resourcesExtension = composeExtension.extensions.create("resources", ResourcesExtension::class.java)

        project.dependencies.extensions.add("compose", Dependencies(project))

        if (!project.buildFile.endsWith(".gradle.kts")) {
            setUpGroovyDslExtensions(project)
        }

        project.initializePreview(desktopExtension)
        composeExtension.extensions.create("web", WebExtension::class.java)

        project.checkComposeCompilerPlugin()

        project.configureComposeResources(resourcesExtension)

        project.configureWebCompatibility()

        project.configureRuntimeLibrariesCompatibilityCheck()

        project.afterEvaluate {
            configureDesktop(project, desktopExtension)
            project.configureWeb(composeExtension)
            project.plugins.withId(KOTLIN_MPP_PLUGIN_ID) {
                val mppExt = project.mppExt
                project.configureExperimentalTargetsFlagsCheck(mppExt)
            }
        }
    }

    @Suppress("DEPRECATION")
    class Dependencies(project: Project) {
        val desktop = DesktopDependencies
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.animation:animation:${ComposeBuildConfig.composeVersion}\""))
        val animation get() = composeDependency("org.jetbrains.compose.animation:animation")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.animation:animation-graphics:${ComposeBuildConfig.composeVersion}\""))
        val animationGraphics get() = composeDependency("org.jetbrains.compose.animation:animation-graphics")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.foundation:foundation:${ComposeBuildConfig.composeVersion}\""))
        val foundation get() = composeDependency("org.jetbrains.compose.foundation:foundation")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.material:material:${ComposeBuildConfig.composeVersion}\""))
        val material get() = composeDependency("org.jetbrains.compose.material:material")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.material3:material3:${ComposeBuildConfig.composeMaterial3Version}\""))
        val material3 get() = composeMaterial3Dependency("org.jetbrains.compose.material3:material3")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.material3:material3-adaptive-navigation-suite:${ComposeBuildConfig.composeMaterial3Version}\""))
        val material3AdaptiveNavigationSuite get() = composeMaterial3Dependency("org.jetbrains.compose.material3:material3-adaptive-navigation-suite")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.runtime:runtime:${ComposeBuildConfig.composeVersion}\""))
        val runtime get() = composeDependency("org.jetbrains.compose.runtime:runtime")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.runtime:runtime-saveable:${ComposeBuildConfig.composeVersion}\""))
        val runtimeSaveable get() = composeDependency("org.jetbrains.compose.runtime:runtime-saveable")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.ui:ui:${ComposeBuildConfig.composeVersion}\""))
        val ui get() = composeDependency("org.jetbrains.compose.ui:ui")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.ui:ui-test:${ComposeBuildConfig.composeVersion}\""))
        @ExperimentalComposeLibrary
        val uiTest get() = composeDependency("org.jetbrains.compose.ui:ui-test")
        @Deprecated("Use io.github.archivesteak.compose.ui:ui-tooling module instead", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.ui:ui-tooling:${ComposeBuildConfig.composeVersion}\""))
        val uiTooling get() = composeDependency("org.jetbrains.compose.ui:ui-tooling")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.ui:ui-util:${ComposeBuildConfig.composeVersion}\""))
        val uiUtil get() = composeDependency("org.jetbrains.compose.ui:ui-util")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.ui:ui-tooling-preview:${ComposeBuildConfig.composeVersion}\""))
        val preview get() = composeDependency("org.jetbrains.compose.ui:ui-tooling-preview")
        @Deprecated(
            "material-icons-extended was retired upstream and is not part of this fork. " +
                "Use Material Symbols or checked-in vector resources.",
            level = DeprecationLevel.ERROR,
        )
        val materialIconsExtended: String
            get() = unsupportedForkDependency("org.jetbrains.compose.material:material-icons-extended")
        @Deprecated("Specify dependency directly")
        val components get() = CommonComponentsDependencies
        @Deprecated("Use compose.html", replaceWith = ReplaceWith("html"), level = DeprecationLevel.ERROR)
        val web: WebDependencies get() = WebDependencies
        @Deprecated("Specify dependency directly")
        val html: HtmlDependencies get() = HtmlDependencies
    }

    @Deprecated("Specify dependency directly")
    object DesktopDependencies {
        @Deprecated("Specify dependency directly")
        val components = DesktopComponentsDependencies

        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.desktop:desktop:${ComposeBuildConfig.composeVersion}\""))
        val common = composeDependency("org.jetbrains.compose.desktop:desktop")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.desktop:desktop-jvm-linux-x64:${ComposeBuildConfig.composeVersion}\""))
        val linux_x64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-linux-x64")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.desktop:desktop-jvm-linux-arm64:${ComposeBuildConfig.composeVersion}\""))
        val linux_arm64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-linux-arm64")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.desktop:desktop-jvm-windows-x64:${ComposeBuildConfig.composeVersion}\""))
        val windows_x64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-windows-x64")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.desktop:desktop-jvm-windows-arm64:${ComposeBuildConfig.composeVersion}\""))
        val windows_arm64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-windows-arm64")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.desktop:desktop-jvm-macos-x64:${ComposeBuildConfig.composeVersion}\""))
        val macos_x64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-macos-x64")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.desktop:desktop-jvm-macos-arm64:${ComposeBuildConfig.composeVersion}\""))
        val macos_arm64 = composeDependency("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64")

        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.ui:ui-test-junit4:${ComposeBuildConfig.composeVersion}\""))
        val uiTestJUnit4 get() = composeDependency("org.jetbrains.compose.ui:ui-test-junit4")

        val currentOs by lazy {
            composeDependency("org.jetbrains.compose.desktop:desktop-jvm-${currentTarget.id}")
        }
    }

    @Deprecated("Specify dependency directly")
    object CommonComponentsDependencies {
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.components:components-resources:${ComposeBuildConfig.composeVersion}\""))
        val resources = composeDependency("org.jetbrains.compose.components:components-resources")
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.components:components-ui-tooling-preview:${ComposeBuildConfig.composeVersion}\""))
        val uiToolingPreview = composeDependency("org.jetbrains.compose.components:components-ui-tooling-preview")
    }

    @Deprecated("Specify dependency directly")
    object DesktopComponentsDependencies {
        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.components:components-splitpane:${ComposeBuildConfig.composeVersion}\""))
        @ExperimentalComposeLibrary
        val splitPane = composeDependency("org.jetbrains.compose.components:components-splitpane")

        @Deprecated("Specify dependency directly", replaceWith = ReplaceWith("\"io.github.archivesteak.compose.components:components-animatedimage:${ComposeBuildConfig.composeVersion}\""))
        @ExperimentalComposeLibrary
        val animatedImage = composeDependency("org.jetbrains.compose.components:components-animatedimage")
    }

    @Deprecated("Use compose.html")
    object WebDependencies {
        @Deprecated("Compose HTML is not published by this fork", level = DeprecationLevel.ERROR)
        val core: String get() = unsupportedForkDependency("org.jetbrains.compose.html:html-core")

        @Deprecated("Compose HTML is not published by this fork", level = DeprecationLevel.ERROR)
        val svg: String get() = unsupportedForkDependency("org.jetbrains.compose.html:html-svg")

        @Deprecated("Compose HTML is not published by this fork", level = DeprecationLevel.ERROR)
        val testUtils: String get() = unsupportedForkDependency("org.jetbrains.compose.html:html-test-utils")
    }

    @Deprecated("Specify dependency directly")
    object HtmlDependencies {
        @Deprecated("Compose HTML is not published by this fork", level = DeprecationLevel.ERROR)
        val core: String get() = unsupportedForkDependency("org.jetbrains.compose.html:html-core")

        @Deprecated("Compose HTML is not published by this fork", level = DeprecationLevel.ERROR)
        val svg: String get() = unsupportedForkDependency("org.jetbrains.compose.html:html-svg")

        @Deprecated("Compose HTML is not published by this fork", level = DeprecationLevel.ERROR)
        val testUtils: String get() = unsupportedForkDependency("org.jetbrains.compose.html:html-test-utils")
    }
}

fun RepositoryHandler.jetbrainsCompose(): MavenArtifactRepository =
    maven { repo -> repo.setUrl("https://packages.jetbrains.team/maven/p/cmp/dev") }

fun KotlinDependencyHandler.compose(groupWithArtifact: String) = composeDependency(groupWithArtifact)

fun DependencyHandler.compose(groupWithArtifact: String) = composeDependency(groupWithArtifact)

private fun composeDependency(groupWithArtifact: String): String {
    val coordinate = remapComposeCoordinate(groupWithArtifact)
    val version = if (coordinate == groupWithArtifact) composeUpstreamVersion else composeVersion
    return "$coordinate:$version"
}

private fun composeMaterial3Dependency(groupWithArtifact: String) =
    "${remapComposeCoordinate(groupWithArtifact)}:$composeMaterial3Version"

private fun unsupportedForkDependency(coordinate: String): Nothing = throw GradleException(
    "$coordinate is not published by the Compose mingw fork. Mixing upstream Compose artifacts " +
        "with io.github.archivesteak Compose artifacts is unsupported."
)

private fun setUpGroovyDslExtensions(project: Project) {
    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        (project.extensions.getByName("kotlin") as? ExtensionAware)?.apply {
            extensions.add("compose", ComposePlugin.Dependencies(project))
        }
    }
    (project.repositories as? ExtensionAware)?.extensions?.apply {
        add("jetbrainsCompose", object : Closure<MavenArtifactRepository>(project.repositories) {
            fun doCall(): MavenArtifactRepository =
                project.repositories.jetbrainsCompose()
        })
    }
}
