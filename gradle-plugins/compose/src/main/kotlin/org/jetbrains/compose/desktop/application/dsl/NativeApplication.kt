/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.application.dsl

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import javax.inject.Inject

abstract class NativeApplication @Inject constructor(
    @Suppress("unused")
    val name: String,
    objects: ObjectFactory
) {
    @get:Inject
    internal abstract val objects: ObjectFactory

    internal val _targets = arrayListOf<KotlinNativeTarget>()
    fun targets(vararg targets: KotlinTarget) {
        val nonNativeTargets = arrayListOf<KotlinTarget>()
        val nonDesktopTargets = arrayListOf<KotlinNativeTarget>()
        for (target in targets) {
            if (target is KotlinNativeTarget) {
                if (target.konanTarget.family == Family.OSX || target.konanTarget.family == Family.MINGW) {
                    _targets.add(target)
                } else {
                    nonDesktopTargets.add(target)
                }
            } else {
                nonNativeTargets.add(target)
            }
        }

        check(nonNativeTargets.isEmpty() && nonDesktopTargets.isEmpty()) {
            buildString {
                appendLine("compose.desktop.nativeApplication.targets supports Kotlin/Native macOS and mingwX64 targets:")
                nonNativeTargets.forEach { appendLine("* '${it.name}' is not a native target;") }
                nonDesktopTargets.forEach { appendLine("* '${it.name}' is not a supported desktop target;") }
            }

        }
    }

    val distributions: NativeApplicationDistributions = objects.newInstance(NativeApplicationDistributions::class.java)
    fun distributions(fn: Action<NativeApplicationDistributions>) {
        fn.execute(distributions)
    }
}

