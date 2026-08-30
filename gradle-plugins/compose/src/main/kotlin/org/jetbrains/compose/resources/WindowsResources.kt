/*
 * Copyright 2020-2026 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.resources

import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.jetbrains.compose.internal.utils.dependsOn
import org.jetbrains.compose.internal.utils.registerOrConfigure
import org.jetbrains.compose.internal.utils.uppercaseFirstChar
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeOutputKind
import org.jetbrains.kotlin.konan.target.Family

private const val WINDOWS_COMPOSE_RESOURCES_ROOT_DIR = "compose-resources"

/**
 * Places the assembled resources beside every Windows executable. This makes the normal Kotlin
 * `run*ExecutableMingwX64` tasks and native test executables use the same stable, executable-relative
 * layout as a packaged application; the runtime therefore never has to guess a project directory.
 */
internal fun Project.configureSyncWindowsComposeResources(
    kotlinExtension: KotlinMultiplatformExtension
) {
    kotlinExtension.targets.withType(KotlinNativeTarget::class.java)
        .matching { target -> target.konanTarget.family == Family.MINGW }
        .all { target ->
            target.binaries.withType(NativeBinary::class.java)
                .matching { binary -> binary.outputKind == NativeOutputKind.EXECUTABLE }
                .all { binary ->
                    val binaryResources = files({
                        (binary.compilation.associatedCompilations + binary.compilation).flatMap { compilation ->
                            compilation.allKotlinSourceSets.map { sourceSet -> sourceSet.resources }
                        }
                    })
                    val copyResources = tasks.registerOrConfigure<Copy>(
                        "copy${binary.name.uppercaseFirstChar()}" +
                            "${target.targetName.uppercaseFirstChar()}ComposeResources"
                    ) {
                        dependsOn(binaryResources)
                        from(binaryResources)
                        into(binary.outputDirectory.resolve(WINDOWS_COMPOSE_RESOURCES_ROOT_DIR))
                    }
                    binary.linkTaskProvider.dependsOn(copyResources)
                }
        }
}
