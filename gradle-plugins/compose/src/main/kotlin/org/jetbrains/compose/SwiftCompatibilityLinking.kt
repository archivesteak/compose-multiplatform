/*
 * Copyright 2026 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose

import org.gradle.api.Project
import org.jetbrains.compose.internal.KOTLIN_MPP_PLUGIN_ID
import org.jetbrains.compose.internal.mppExt
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBinary
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

/**
 * Adds the active Xcode's Swift compatibility-library search path to iOS linker tasks.
 *
 * A published klib cannot contain an absolute path to the Xcode installation that produced it.
 * The selected Xcode toolchain is therefore resolved lazily in the consuming build, immediately
 * before Kotlin/Native builds the linker invocation.
 */
internal fun Project.configureSwiftCompatibilityLinking() {
    if (System.getProperty("os.name") != "Mac OS X") return

    plugins.withId(KOTLIN_MPP_PLUGIN_ID) {
        mppExt.targets.withType(KotlinNativeTarget::class.java).all { target ->
            val sdkName = when (target.konanTarget) {
                KonanTarget.IOS_ARM64 -> "iphoneos"
                KonanTarget.IOS_SIMULATOR_ARM64 -> "iphonesimulator"
                else -> null
            }
            if (sdkName != null) {
                val swiftCompatibilityLibraryDir = providers.exec { spec ->
                    spec.commandLine("xcrun", "--find", "swiftc")
                }.standardOutput.asText.map { swiftcPath ->
                    File(swiftcPath.trim()).parentFile.parentFile.parentFile
                        .resolve("usr/lib/swift/$sdkName")
                        .absolutePath
                }

                target.binaries.withType(NativeBinary::class.java).all { binary ->
                    binary.linkTaskProvider.configure { linkTask ->
                        linkTask.toolOptions.freeCompilerArgs.addAll(
                            swiftCompatibilityLibraryDir.map { libraryDir ->
                                listOf("-linker-option", "-L$libraryDir")
                            }
                        )
                    }
                }
            }
        }
    }
}
