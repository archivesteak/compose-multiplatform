package org.jetbrains.compose.resources

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultMingwX64ResourceReaderTest {
    @Test
    fun readsAssembledUnicodeResourceBesideTestExecutable() = runTest {
        val expected = "Windows reader — Привет 🙂"

        assertEquals(expected, DefaultMingwX64ResourceReader.read(RESOURCE_PATH).decodeToString().trimEnd())
        assertContentEquals(
            "reader".encodeToByteArray(),
            DefaultMingwX64ResourceReader.readPart(RESOURCE_PATH, offset = 8, size = 6)
        )

        val uri = DefaultMingwX64ResourceReader.getUri(RESOURCE_PATH)
        assertTrue(uri.startsWith("file:///"), uri)
        assertTrue("%F0%9F%99%82" in uri, uri)
    }

    @Test
    fun rejectsAbsoluteAndTraversalPaths() {
        listOf(
            "",
            ".",
            "..",
            "../secret",
            "composeResources/../secret",
            "/absolute/resource",
            "C:/absolute/resource",
            "composeResources\\windows\\resource",
            "composeResources/invalid\u0000resource"
        ).forEach { path ->
            assertFailsWith<IllegalArgumentException>(path) {
                DefaultMingwX64ResourceReader.getUri(path)
            }
        }
    }

    @Test
    fun createsEncodedDriveAndNetworkFileUris() {
        val encodedPath = "%D0%9F%D1%80%D0%B8%D0%B2%D0%B5%D1%82%20%F0%9F%99%82/file.txt"
        assertEquals(
            "file:///C:/Program%20Files/$encodedPath",
            "C:\\Program Files\\Привет 🙂\\file.txt".toWindowsFileUri()
        )
        assertEquals(
            "file://server/share/$encodedPath",
            "\\\\server\\share\\Привет 🙂\\file.txt".toWindowsFileUri()
        )
        assertEquals(
            "file://server/share/$encodedPath",
            "\\\\?\\UNC\\server\\share\\Привет 🙂\\file.txt".toWindowsFileUri()
        )
        assertEquals(
            "file:///C:/Program%20Files/$encodedPath",
            "\\\\?\\C:\\Program Files\\Привет 🙂\\file.txt".toWindowsFileUri()
        )
    }

    @Test
    fun exposesRealWindowsResourceEnvironment() {
        val environment = getSystemEnvironment()

        assertTrue(environment.language.language.isNotBlank())
        assertTrue(environment.density in DensityQualifier.entries)
        assertTrue(environment.theme in ThemeQualifier.entries)
    }

    private companion object {
        const val RESOURCE_PATH =
            "composeResources/library.generated.resources/files/windows-reader-🙂.txt"
    }
}
