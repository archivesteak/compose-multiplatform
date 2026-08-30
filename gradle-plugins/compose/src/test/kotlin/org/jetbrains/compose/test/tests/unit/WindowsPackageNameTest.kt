/*
 * Copyright 2020-2026 JetBrains s.r.o. and respective authors and developers.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE.txt file.
 */

package org.jetbrains.compose.test.tests.unit

import org.jetbrains.compose.desktop.application.tasks.windowsExecutableFileName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WindowsPackageNameTest {
    @Test
    fun createsExecutableName() {
        assertEquals("Test Resources.exe", windowsExecutableFileName("Test Resources"))
        assertEquals("Приложение.exe", windowsExecutableFileName("Приложение"))
    }

    @Test
    fun rejectsTraversalInvalidAndReservedNames() {
        listOf(
            "",
            " ../escape",
            "../escape",
            "folder/app",
            "folder\\app",
            "bad:name",
            "trailing.",
            "CON",
            "nul.txt",
            "CON .txt",
            "COM1",
            "COM1 .log",
            "LPT³.log"
        ).forEach { packageName ->
            assertFailsWith<IllegalArgumentException>(packageName) {
                windowsExecutableFileName(packageName)
            }
        }
    }
}
