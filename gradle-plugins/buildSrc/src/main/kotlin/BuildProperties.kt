/*
 * Copyright 2020-2021 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

import org.gradle.api.Project

// "Global" properties
object BuildProperties {
    const val name = "Compose Multiplatform MinGW Plugin"
    const val group = "io.github.archivesteak.compose"
    const val website = "https://github.com/archivesteak/compose-multiplatform"
    const val vcs = "https://github.com/archivesteak/compose-multiplatform"
    fun composeVersion(project: Project): String = project.exactVersion(
        property = "compose.version",
        expected = "1.12.0-beta02-mingw",
        legacyEnvironmentOverride = "COMPOSE_GRADLE_PLUGIN_COMPOSE_VERSION",
    )
    fun composeUpstreamVersion(project: Project): String = project.exactVersion(
        property = "compose.upstream.version",
        expected = "1.12.0-beta02",
    )
    fun composeMaterial3Version(project: Project): String = project.exactVersion(
        property = "compose.material3.version",
        expected = "1.12.0-alpha03-mingw",
    )
    fun testsAndroidxCompilerVersion(project: Project): String =
        project.findProperty("compose.tests.androidx.compiler.version") as String
    fun testsAndroidxCompilerCompatibleVersion(project: Project): String =
        project.findProperty("compose.tests.androidx.compatible.kotlin.version") as String
    fun deployVersion(project: Project): String = project.exactVersion(
        property = "deploy.version",
        expected = "1.12.0-beta02-mingw",
        legacyEnvironmentOverride = "COMPOSE_GRADLE_PLUGIN_VERSION",
    )
}

private fun Project.exactVersion(
    property: String,
    expected: String,
    legacyEnvironmentOverride: String? = null,
): String {
    val configured = findProperty(property)?.toString()
    require(configured == expected) {
        "The fork requires $property=$expected, but found ${configured ?: "<missing>"}"
    }
    legacyEnvironmentOverride?.let { environmentName ->
        val environmentValue = System.getenv(environmentName)
        require(environmentValue == null || environmentValue == expected) {
            "$environmentName cannot override the exact fork version $expected; found $environmentValue"
        }
    }
    return expected
}
