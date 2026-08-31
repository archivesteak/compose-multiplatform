package org.jetbrains.compose.resources

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.wcstr
import platform.posix.SEEK_SET
import platform.posix._fseeki64
import platform.posix._wfopen
import platform.posix.fclose
import platform.posix.ferror
import platform.posix.fread
import platform.windows.GetModuleFileNameW
import platform.windows.WCHARVar

@ExperimentalResourceApi
internal actual fun getPlatformResourceReader(): ResourceReader = DefaultMingwX64ResourceReader

/**
 * Reads resources from the file system. There is no bundle or class loader on Windows, so both
 * Gradle-run and packaged executables keep their assembled resources in an executable-relative
 * `compose-resources/` directory.
 */
@ExperimentalResourceApi
internal object DefaultMingwX64ResourceReader : ResourceReader {
    override suspend fun read(path: String): ByteArray {
        val file = openFile(getPathOnDisk(path)) ?: throw MissingResourceException(path)
        try {
            val chunks = mutableListOf<ByteArray>()
            var total = 0L
            while (true) {
                val chunk = ByteArray(CHUNK_SIZE)
                val read = chunk.usePinned {
                    fread(it.addressOf(0), 1UL, CHUNK_SIZE.toULong(), file).toInt()
                }
                if (read > 0) {
                    chunks.add(if (read == CHUNK_SIZE) chunk else chunk.copyOf(read))
                    total += read
                    if (total > Int.MAX_VALUE) {
                        throw MissingResourceException(path, "File is too long")
                    }
                }
                if (read < CHUNK_SIZE && ferror(file) != 0) {
                    throw MissingResourceException(path, "Unable to read the file")
                }
                if (read < CHUNK_SIZE) break
            }
            val bytes = ByteArray(total.toInt())
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(bytes, offset)
                offset += chunk.size
            }
            return bytes
        } finally {
            fclose(file)
        }
    }

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
        require(offset >= 0) { "offset must be non-negative" }
        require(size >= 0) { "size must be non-negative" }
        if (size > Int.MAX_VALUE) throw MissingResourceException(path, "Requested part is too long")
        val pathOnDisk = getPathOnDisk(path)
        if (size == 0L) return ByteArray(0)

        val file = openFile(pathOnDisk) ?: throw MissingResourceException(path)
        try {
            if (_fseeki64(file, offset, SEEK_SET) != 0) {
                throw MissingResourceException(path, "Unable to seek to offset $offset")
            }

            val bytes = ByteArray(size.toInt())
            var total = 0
            while (total < bytes.size) {
                val read = bytes.usePinned {
                    fread(it.addressOf(total), 1UL, (bytes.size - total).toULong(), file).toInt()
                }
                if (read <= 0) break
                total += read
            }
            if (total < bytes.size && ferror(file) != 0) {
                throw MissingResourceException(path, "Unable to read the requested part")
            }
            return if (total == bytes.size) bytes else bytes.copyOf(total)
        } finally {
            fclose(file)
        }
    }

    override fun getUri(path: String): String =
        getPathOnDisk(path).toWindowsFileUri()

    private fun getPathOnDisk(path: String): String {
        validateWindowsResourcePath(path)

        val exeDir = exeDirectory() ?: throw MissingResourceException(path, "Executable path is unavailable")
        val installed = "$exeDir/$COMPOSE_RESOURCES_ROOT_DIR/$path"
        if (fileExists(installed)) return installed
        throw MissingResourceException(path)
    }

    private fun fileExists(path: String): Boolean {
        val file = openFile(path) ?: return false
        fclose(file)
        return true
    }

    private fun openFile(path: String) = memScoped {
        _wfopen(path.toExtendedLengthWindowsPath().wcstr.ptr, "rb".wcstr.ptr)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun exeDirectory(): String? = memScoped {
        val buffer = allocArray<WCHARVar>(WINDOWS_PATH_BUFFER_SIZE)
        val length = GetModuleFileNameW(null, buffer, WINDOWS_PATH_BUFFER_SIZE.convert())
        if (length == 0u || length >= WINDOWS_PATH_BUFFER_SIZE.toUInt()) return@memScoped null
        buffer.toKStringFromUtf16().substringBeforeLast('\\').ifEmpty { null }
    }
}

private fun validateWindowsResourcePath(path: String) {
    require(path.isNotEmpty()) { "Resource path must not be empty" }
    require(!path.startsWith('/')) { "Resource path must be relative: $path" }
    val segments = path.split('/')
    require(segments.none { it.isEmpty() || it == "." || it == ".." }) {
        "Resource path must not contain empty or traversal segments: $path"
    }
    segments.forEach { segment ->
        require(segment.length <= MAX_WINDOWS_PATH_COMPONENT_LENGTH) {
            "Resource path component is too long: $segment"
        }
        require(segment == segment.trimEnd(' ', '.')) {
            "Resource path component must not end with a space or dot: $segment"
        }
        require(segment.none { it.code < 32 || it in WINDOWS_INVALID_PATH_CHARS }) {
            "Resource path contains an invalid Windows file-name character: $path"
        }
        // Win32 normalizes trailing ASCII spaces and periods in the base name before checking the
        // DOS device namespace. Apply the same rule as the Windows package-name validator so a
        // component such as "CON .txt" cannot bypass the reserved-name check.
        val deviceName = segment.substringBefore('.')
            .trimEnd(' ', '.')
            .uppercase()
        require(deviceName !in WINDOWS_RESERVED_DEVICE_NAMES) {
            "Resource path contains a reserved Windows device name: $segment"
        }
    }
}

private fun String.toExtendedLengthWindowsPath(): String {
    val windowsPath = replace('/', '\\')
    return when {
        windowsPath.startsWith("\\\\?\\") -> windowsPath
        windowsPath.startsWith("\\\\") -> "\\\\?\\UNC\\" + windowsPath.substring(2)
        else -> "\\\\?\\$windowsPath"
    }
}

private const val CHUNK_SIZE = 64 * 1024
private const val WINDOWS_PATH_BUFFER_SIZE = 32 * 1024
private const val COMPOSE_RESOURCES_ROOT_DIR = "compose-resources"
private const val URI_HEX_DIGITS = "0123456789ABCDEF"
private const val MAX_WINDOWS_PATH_COMPONENT_LENGTH = 255
private const val WINDOWS_INVALID_PATH_CHARS = "<>:\"\\|?*"
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

internal fun String.toWindowsFileUri(): String {
    val normalized = replace('\\', '/')
    val (prefix, path) = when {
        normalized.startsWith("//?/UNC/", ignoreCase = true) -> "file://" to normalized.substring(8)
        normalized.startsWith("//?/", ignoreCase = true) -> "file:///" to normalized.substring(4)
        normalized.startsWith("//") -> "file:" to normalized
        else -> "file:///" to normalized
    }
    return prefix + path.percentEncodeFileUriPath()
}

private fun String.percentEncodeFileUriPath(): String = buildString(length) {
    for (byte in this@percentEncodeFileUriPath.encodeToByteArray()) {
        val value = byte.toInt() and 0xff
        val isUnreserved =
            value in 'a'.code..'z'.code ||
                value in 'A'.code..'Z'.code ||
                value in '0'.code..'9'.code ||
                value == '-'.code || value == '.'.code || value == '_'.code || value == '~'.code
        if (isUnreserved || value == '/'.code || value == ':'.code) {
            append(value.toChar())
        } else {
            append('%')
            append(URI_HEX_DIGITS[value ushr 4])
            append(URI_HEX_DIGITS[value and 0x0f])
        }
    }
}
