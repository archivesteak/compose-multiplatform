/*
 * Copyright 2020-2022 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.web.internal

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Usage
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.internal.FORK_COMPOSE_ROOT_GROUP
import org.jetbrains.compose.internal.FORK_SKIKO_GROUP
import org.jetbrains.compose.internal.utils.detachedDependency
import org.jetbrains.compose.internal.utils.file
import org.jetbrains.compose.internal.utils.registerTask
import org.jetbrains.compose.web.WebExtension
import org.jetbrains.compose.web.tasks.UnpackSkikoWasmRuntimeTask
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.targets.js.ir.Executable
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

internal fun Project.configureWeb(
    composeExt: ComposeExtension,
) {
    val webExt = composeExt.extensions.getByType(WebExtension::class.java)

    // here we check all dependencies (including transitive)
    // If there is compose.ui, then skiko is required!
    val composeUiRuntime = project.provider {
        val coordinates = webExt.targetsToConfigure(project).flatMap { target ->
            val compilation = target.compilations.getByName("main")
            val compileConfiguration = compilation.compileDependencyConfigurationName
            val runtimeConfiguration = compilation.runtimeDependencyConfigurationName

            listOf(compileConfiguration, runtimeConfiguration).mapNotNull {  name ->
                project.configurations.findByName(name)
            }.flatMap { configuration ->
                configuration.incoming.resolutionResult.allComponents.map { it.id }
            }.mapNotNull { identifier ->
                (identifier as? ModuleComponentIdentifier)
                    ?.takeIf { it.group in COMPOSE_UI_GROUPS && it.module == "ui" }
                    ?.let { ComposeUiCoordinates(it.group, it.version) }
            }
        }
        ComposeUiRuntime(selectComposeUiCoordinates(coordinates))
    }

    val targets = webExt.targetsToConfigure(project)

    // configure only if there is k/wasm or k/js target:
    if (targets.isNotEmpty()) {
        configureWebApplication(targets, project, composeUiRuntime)
    }
}

internal fun configureWebApplication(
    targets: Collection<KotlinJsIrTarget>,
    project: Project,
    composeUiRuntime: Provider<ComposeUiRuntime>
) {
    // Keep the established lifecycle task names for build scripts that inspect them, while each
    // target resolves and unpacks the runtime variant matching its own JS/Wasm attributes.
    val unpackAllWebRuntimes = project.registerTask<DefaultTask>("unpackSkikoWasmRuntime") {}
    val processAllWasmRuntimes = project.registerTask<DefaultTask>("processSkikoRuntimeForKWasm") {}

    targets.forEach { target ->
        val titledTargetName = target.name.replaceFirstChar { it.titlecase() }
        val mainCompilation = target.compilations.getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
        val targetRuntimeConfiguration = project.configurations.getByName(
            mainCompilation.runtimeDependencyConfigurationName
        )
        val upstreamRuntime = project.configurations.create(
            "composeUpstreamSkikoRuntimeFor$titledTargetName"
        ).apply {
            isCanBeConsumed = false
            defaultDependencies { dependencies ->
                composeUiRuntime.get().coordinates
                    ?.takeIf { it.group == UPSTREAM_COMPOSE_UI_GROUP }
                    ?.let { dependencies.add(project.skikoWebRuntimeDependency(it)) }
            }
        }
        val forkRuntime = project.configurations.create(
            "composeForkSkikoRuntimeFor$titledTargetName"
        ).apply {
            isCanBeConsumed = false
            copySkikoRuntimeAttributes(
                project,
                from = targetRuntimeConfiguration.attributes,
                to = attributes,
            )
            defaultDependencies { dependencies ->
                composeUiRuntime.get().coordinates
                    ?.takeIf { it.group == FORK_COMPOSE_UI_GROUP }
                    ?.let { dependencies.add(project.skikoWebRuntimeDependency(it)) }
            }
        }
        val skikoRuntimeFiles = upstreamRuntime + forkRuntime
        val unpackedRuntimeDir = project.layout.buildDirectory.dir(
            "compose/skiko-${target.name}-for-web-runtime"
        )
        val unpackRuntime = project.registerTask<UnpackSkikoWasmRuntimeTask>(
            "unpackSkikoWasmRuntimeFor$titledTargetName"
        ) {
            onlyIf { composeUiRuntime.get().coordinates != null }
            this.skikoRuntimeFiles = skikoRuntimeFiles
            outputDir.set(unpackedRuntimeDir)
        }
        unpackAllWebRuntimes.configure { task -> task.dependsOn(unpackRuntime) }

        val processWasmRuntime = if (target.wasmTargetType != null) {
            project.registerTask<Copy>("processSkikoRuntimeForK${titledTargetName}") {
                dependsOn(unpackRuntime)
                from(unpackedRuntimeDir)
                into(project.layout.buildDirectory.dir("compose/skiko-${target.name}-runtime-processed"))
            }.also { processTask ->
                processAllWasmRuntimes.configure { task -> task.dependsOn(processTask) }
            }
        } else {
            null
        }

        target.compilations.all { compilation ->
            // `wasmTargetType` is available starting with kotlin 1.9.2x
            if (target.wasmTargetType != null) {
                val processTask = checkNotNull(processWasmRuntime)
                // Kotlin/Wasm uses ES module system to depend on skiko through skiko.mjs.
                // Further bundler could process all files by its own (both skiko.mjs and skiko.wasm) and then emits its own version.
                // So that’s why we need to provide skiko.mjs and skiko.wasm only for webpack, but not in the final dist.
                compilation.binaries.all {
                    it.linkSyncTask.configure {
                        it.dependsOn(processTask)
                        it.from.from(processTask.map { task -> task.destinationDir })
                    }
                }
            } else {
                // Kotlin/JS depends on Skiko through global space.
                // Bundler cannot know anything about global externals, so that’s why we need to copy it to final dist
                project.tasks.named(compilation.processResourcesTaskName, ProcessResources::class.java) {
                    it.from(unpackedRuntimeDir)
                    it.dependsOn(unpackRuntime)
                    it.exclude("META-INF")
                }
            }
        }

        configureComposeUiTestExecutableCheck(project, target)
    }
}

private fun configureComposeUiTestExecutableCheck(
    project: Project,
    target: KotlinJsIrTarget,
) {
    val titledTargetName = target.name.replaceFirstChar { it.titlecase() }
    val checkTask = project.registerTask<CheckComposeUiTestExecutableTask>(
        "checkComposeUiTestConfigurationFor$titledTargetName"
    ) {
        targetName.set(target.name)
        // Computed lazily, after all `afterEvaluate`s: `binaries.executable()` may be declared
        // after this plugin runs, so the binaries set can still be empty here. Reading these
        // through providers (instead of in the task action) also keeps the task free of
        // Project/target references, so it stays compatible with the configuration cache.
        testDependsOnSkiko.set(project.provider { project.testCompilationDependsOnSkiko(target) })
        hasExecutableBinary.set(
            project.provider { target.binaries.withType(Executable::class.java).isNotEmpty() }
        )
    }

    project.tasks.withType(KotlinJsTest::class.java).configureEach { testTask ->
        val compilation = testTask.compilation
        // Browser test tasks (Karma) are named "<target>BrowserTest"; node tests don't run Compose UI.
        if (compilation.target == target &&
            compilation.compilationName == KotlinCompilation.TEST_COMPILATION_NAME &&
            testTask.name.endsWith("BrowserTest")
        ) {
            testTask.dependsOn(checkTask)
        }
    }
}

/**
 * Compose UI browser tests must be bundled by webpack to load the Skiko runtime, which only
 * happens when the target declares an executable `binaries.executable()`. When a target that
 * depends on Skiko has no executable, this task fails with an actionable message instead of
 * letting the tests fail in a confusing way.
 */
@DisableCachingByDefault(because = "Not worth caching: only validates the configuration")
internal abstract class CheckComposeUiTestExecutableTask : DefaultTask() {
    @get:Input
    abstract val targetName: Property<String>

    @get:Input
    abstract val testDependsOnSkiko: Property<Boolean>

    @get:Input
    abstract val hasExecutableBinary: Property<Boolean>

    @TaskAction
    fun check() {
        if (!hasExecutableBinary.get() && testDependsOnSkiko.get()) {
            val target = targetName.get()
            throw GradleException(
                "Compose UI tests for the '$target' target are not bundled with webpack: " +
                        "no executable binary is declared, so the Skiko runtime required by Compose UI " +
                        "cannot be loaded and the tests may fail. Add `binaries.executable()` to the " +
                        "'$target' target. See https://youtrack.jetbrains.com/issue/CMP-4906"
            )
        }
    }
}

private fun Project.testCompilationDependsOnSkiko(target: KotlinJsIrTarget): Boolean {
    val testCompilation = target.compilations.findByName(KotlinCompilation.TEST_COMPILATION_NAME)
        ?: return false
    return listOf(
        testCompilation.compileDependencyConfigurationName, testCompilation.runtimeDependencyConfigurationName
    ).mapNotNull { name ->
        configurations.findByName(name)
    }.any { configuration ->
        configuration.allDependenciesDescriptors.any(::isSkikoDependency)
    }
}

internal data class ComposeUiCoordinates(val group: String, val version: String)

internal data class ComposeUiRuntime(val coordinates: ComposeUiCoordinates?)

internal data class SkikoRuntimeModule(val group: String, val artifact: String)

internal fun selectComposeUiCoordinates(
    coordinates: Iterable<ComposeUiCoordinates>
): ComposeUiCoordinates? {
    val distinctCoordinates = coordinates.toSet()
    if (distinctCoordinates.size > 1) {
        throw GradleException(
            "Compose UI dependencies must use one group and version in a project; found " +
                distinctCoordinates.sortedWith(compareBy(ComposeUiCoordinates::group, ComposeUiCoordinates::version))
                    .joinToString { "${it.group}:ui:${it.version}" }
        )
    }
    return distinctCoordinates.singleOrNull()
}

internal fun skikoRuntimeModuleForComposeUiGroup(composeUiGroup: String): SkikoRuntimeModule = when (composeUiGroup) {
    UPSTREAM_COMPOSE_UI_GROUP -> SkikoRuntimeModule(
        group = UPSTREAM_SKIKO_GROUP,
        artifact = "skiko-js-wasm-runtime",
    )
    FORK_COMPOSE_UI_GROUP -> SkikoRuntimeModule(
        group = FORK_SKIKO_GROUP,
        artifact = "skiko",
    )
    else -> throw GradleException("Unsupported Compose UI group '$composeUiGroup'")
}

internal fun copySkikoRuntimeAttributes(
    project: Project,
    from: AttributeContainer,
    to: AttributeContainer,
) {
    from.keySet().forEach { attribute ->
        @Suppress("UNCHECKED_CAST")
        to.attribute(
            attribute as Attribute<Any>,
            checkNotNull(from.getAttribute(attribute)) as Any,
        )
    }
    to.attribute(
        Usage.USAGE_ATTRIBUTE,
        project.objects.named(Usage::class.java, SKIKO_RUNTIME_USAGE),
    )
}

private const val UPSTREAM_COMPOSE_UI_GROUP = "org.jetbrains.compose.ui"
private const val UPSTREAM_SKIKO_GROUP = "org.jetbrains.skiko"
private const val FORK_COMPOSE_UI_GROUP = "$FORK_COMPOSE_ROOT_GROUP.ui"
private const val SKIKO_RUNTIME_USAGE = "skiko-runtime"
private val COMPOSE_UI_GROUPS = setOf(UPSTREAM_COMPOSE_UI_GROUP, FORK_COMPOSE_UI_GROUP)
private val SKIKO_GROUPS = setOf(UPSTREAM_SKIKO_GROUP, FORK_SKIKO_GROUP)

private fun Project.skikoWebRuntimeDependency(composeUi: ComposeUiCoordinates): Dependency {
    val runtimeModule = skikoRuntimeModuleForComposeUiGroup(composeUi.group)
    val configurationWithSkiko = detachedDependency(
        artifactId = "ui-graphics",
        groupId = composeUi.group,
        version = composeUi.version,
    )
    val skikoVersions = configurationWithSkiko.allDependenciesDescriptors
        .filter { dependency -> dependency.group == runtimeModule.group }
        .mapNotNull(DependencyDescriptor::version)
        .toSet()
    if (skikoVersions.size != 1) {
        error(
            "Cannot determine one Skiko version from ${composeUi.group}:ui-graphics:${composeUi.version}; " +
                "found ${skikoVersions.sorted()}"
        )
    }
    return dependencies.create("${runtimeModule.group}:${runtimeModule.artifact}:${skikoVersions.single()}")
}

private fun isSkikoDependency(dep: DependencyDescriptor): Boolean =
    dep.group in SKIKO_GROUPS && dep.version != null

private val Configuration.allDependenciesDescriptors: Sequence<DependencyDescriptor>
    get() = with (resolvedConfiguration.lenientConfiguration) {
        allModuleDependencies.asSequence().map { ResolvedDependencyDescriptor(it) } +
                unresolvedModuleDependencies.asSequence().map { UnresolvedDependencyDescriptor(it) }
    }

private abstract class DependencyDescriptor {
    abstract val group: String?
    abstract val name: String?
    abstract val version: String?
}

private class ResolvedDependencyDescriptor(private val dependency: ResolvedDependency) : DependencyDescriptor() {
    override val group: String?
        get() = dependency.moduleGroup

    override val name: String?
        get() = dependency.moduleName

    override val version: String?
        get() = dependency.moduleVersion
}

private class UnresolvedDependencyDescriptor(private val dependency: UnresolvedDependency) : DependencyDescriptor() {
    override val group: String?
        get() = dependency.selector.group

    override val name: String?
        get() = dependency.selector.name

    override val version: String?
        get() = dependency.selector.version
}
