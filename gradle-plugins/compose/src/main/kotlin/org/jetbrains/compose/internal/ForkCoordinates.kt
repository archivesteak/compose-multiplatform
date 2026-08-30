/*
 * Copyright 2020-2026 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.internal

internal const val FORK_COMPOSE_ROOT_GROUP = "io.github.archivesteak.compose"
internal const val FORK_SKIKO_GROUP = "io.github.archivesteak.skiko"
internal const val FORK_COMPONENTS_GROUP = "$FORK_COMPOSE_ROOT_GROUP.components"
internal const val FORK_RESOURCES_COORDINATE = "$FORK_COMPONENTS_GROUP:components-resources"

private val forkedComposeGroups = mapOf(
    "org.jetbrains.compose.animation" to "$FORK_COMPOSE_ROOT_GROUP.animation",
    "org.jetbrains.compose.desktop" to "$FORK_COMPOSE_ROOT_GROUP.desktop",
    "org.jetbrains.compose.foundation" to "$FORK_COMPOSE_ROOT_GROUP.foundation",
    "org.jetbrains.compose.material" to "$FORK_COMPOSE_ROOT_GROUP.material",
    "org.jetbrains.compose.material3" to "$FORK_COMPOSE_ROOT_GROUP.material3",
    "org.jetbrains.compose.runtime" to "$FORK_COMPOSE_ROOT_GROUP.runtime",
    "org.jetbrains.compose.ui" to "$FORK_COMPOSE_ROOT_GROUP.ui",
)

private val upstreamOnlyCoordinates = setOf(
    "org.jetbrains.compose.material:material-icons-core",
    "org.jetbrains.compose.material:material-icons-extended",
)

/**
 * Maps the Compose core groups published by this fork and the one component built here. The
 * retired icon artifacts and the HTML/other components modules keep their upstream coordinates.
 */
internal fun remapComposeCoordinate(groupWithArtifact: String): String {
    if (groupWithArtifact in upstreamOnlyCoordinates) return groupWithArtifact

    if (groupWithArtifact == "org.jetbrains.compose.components:components-resources") {
        return FORK_RESOURCES_COORDINATE
    }

    val separator = groupWithArtifact.indexOf(':')
    if (separator < 0) return groupWithArtifact
    val group = groupWithArtifact.substring(0, separator)
    val forkGroup = forkedComposeGroups[group] ?: return groupWithArtifact
    return forkGroup + groupWithArtifact.substring(separator)
}
