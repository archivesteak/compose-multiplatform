/*
 * Copyright 2020-2026 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.desktop.application.tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.compose.desktop.tasks.AbstractComposeDesktopTask
import org.jetbrains.compose.internal.utils.clearDirs
import org.jetbrains.compose.internal.utils.ioFile
import org.jetbrains.compose.internal.utils.property
import java.util.Locale

/**
 * Creates a directly runnable Kotlin/Native Windows application directory. Kotlin/Native already
 * produces the executable, so this task deliberately does not invoke jpackage or create an
 * installer; it only gives the executable its distribution name and places Compose resources next
 * to it in the layout expected by the mingwX64 resource reader.
 */
@DisableCachingByDefault(because = "Copies a locally linked native executable")
abstract class AbstractNativeWindowsApplicationPackageAppDirTask : AbstractComposeDesktopTask() {
    @get:Input
    val packageName: Property<String> = objects.property()

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val executable: RegularFileProperty = objects.fileProperty()

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val composeResourcesDirs: ConfigurableFileCollection = objects.fileCollection()

    @get:OutputDirectory
    val destinationDir: DirectoryProperty = objects.directoryProperty()

    @TaskAction
    fun run() {
        fileOperations.clearDirs(destinationDir)
        val outputDir = destinationDir.ioFile.apply { mkdirs() }
        executable.ioFile.copyTo(outputDir.resolve(windowsExecutableFileName(packageName.get())))

        if (!composeResourcesDirs.isEmpty) {
            fileOperations.copy { copySpec ->
                copySpec.from(composeResourcesDirs)
                copySpec.into(outputDir.resolve("compose-resources"))
            }
        }

        logger.lifecycle("The native Windows application is written to ${outputDir.canonicalPath}")
    }
}

internal fun windowsExecutableFileName(packageName: String): String {
    require(packageName.isNotBlank()) { "Windows package name must not be blank" }
    require(packageName == packageName.trim()) {
        "Windows package name must not start or end with whitespace: '$packageName'"
    }
    require(packageName.length <= MAX_WINDOWS_PACKAGE_NAME_LENGTH) {
        "Windows package name is too long: '$packageName'"
    }
    require(packageName.none { it.code < 32 || it in WINDOWS_INVALID_FILE_NAME_CHARS }) {
        "Windows package name contains an invalid file-name character: '$packageName'"
    }
    require(!packageName.endsWith('.')) { "Windows package name must not end with a dot: '$packageName'" }

    val deviceName = packageName.substringBefore('.').uppercase(Locale.ROOT)
    require(deviceName !in WINDOWS_RESERVED_DEVICE_NAMES) {
        "Windows package name is a reserved device name: '$packageName'"
    }
    return "$packageName.exe"
}

private const val MAX_WINDOWS_PACKAGE_NAME_LENGTH = 251 // leave room for the appended ".exe"
private const val WINDOWS_INVALID_FILE_NAME_CHARS = "<>:\"/\\|?*"
private val WINDOWS_RESERVED_DEVICE_NAMES = buildSet {
    addAll(listOf("CON", "PRN", "AUX", "NUL", "CONIN\$", "CONOUT\$"))
    (1..9).forEach { index ->
        add("COM$index")
        add("LPT$index")
    }
    listOf('¹', '²', '³').forEach { index ->
        add("COM$index")
        add("LPT$index")
    }
}
