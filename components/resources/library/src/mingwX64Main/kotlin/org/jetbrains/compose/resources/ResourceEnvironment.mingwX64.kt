package org.jetbrains.compose.resources

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKStringFromUtf16
import kotlinx.cinterop.value
import platform.windows.DWORDVar
import platform.windows.ERROR_SUCCESS
import platform.windows.GetDC
import platform.windows.GetDeviceCaps
import platform.windows.GetUserDefaultLocaleName
import platform.windows.HKEYVar
import platform.windows.HKEY_CURRENT_USER
import platform.windows.KEY_READ
import platform.windows.LOCALE_NAME_MAX_LENGTH
import platform.windows.LOGPIXELSY
import platform.windows.RegCloseKey
import platform.windows.RegOpenKeyExW
import platform.windows.RegQueryValueExW
import platform.windows.ReleaseDC
import platform.windows.REG_DWORD
import platform.windows.WCHARVar

@OptIn(ExperimentalForeignApi::class)
internal actual fun getSystemEnvironment(): ResourceEnvironment {
    val subtags = userDefaultLocaleName().orEmpty().split('-', '_')
    val language = subtags.firstOrNull().orEmpty()
    // BCP 47: an optional 4-letter script sits between language and region ("zh-Hant-TW").
    val script = subtags.getOrNull(1)
        ?.takeIf { it.length == 4 && it.all(Char::isLetter) }
        .orEmpty()
    val region = subtags.getOrNull(if (script.isEmpty()) 1 else 2)
        ?.takeIf { subtag ->
            (subtag.length == 2 && subtag.all(Char::isLetter)) ||
                (subtag.length == 3 && subtag.all(Char::isDigit))
        }
        .orEmpty()

    return ResourceEnvironment(
        language = LanguageQualifier(language),
        script = ScriptQualifier(script),
        region = RegionQualifier(region),
        theme = ThemeQualifier.selectByValue(isDarkTheme()),
        density = DensityQualifier.selectByValue(systemDpi())
    )
}

/** The user's locale, as a BCP 47 tag like "en-US". Null when Windows cannot name one. */
@OptIn(ExperimentalForeignApi::class)
private fun userDefaultLocaleName(): String? = memScoped {
    val buffer = allocArray<WCHARVar>(LOCALE_NAME_MAX_LENGTH)
    val length = GetUserDefaultLocaleName(buffer, LOCALE_NAME_MAX_LENGTH)
    if (length <= 1) null else buffer.toKStringFromUtf16()
}

/**
 * Whether Windows is in dark app mode. The setting lives in
 * `HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize\AppsUseLightTheme`
 * (0 = dark); when the key or value is absent the honest answer is light.
 */
@OptIn(ExperimentalForeignApi::class)
private fun isDarkTheme(): Boolean = memScoped {
    val key = alloc<HKEYVar>()
    val opened = RegOpenKeyExW(
        HKEY_CURRENT_USER,
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
        0u,
        KEY_READ.convert(),
        key.ptr
    )
    if (opened != ERROR_SUCCESS) return@memScoped false
    val value = alloc<DWORDVar>()
    val type = alloc<DWORDVar>()
    val size = alloc<DWORDVar> { this.value = sizeOf<DWORDVar>().convert() }
    val read = RegQueryValueExW(
        key.value,
        "AppsUseLightTheme",
        null,
        type.ptr,
        value.ptr.reinterpret(),
        size.ptr
    )
    RegCloseKey(key.value)
    if (
        read != ERROR_SUCCESS ||
        type.value != REG_DWORD.toUInt() ||
        size.value != sizeOf<DWORDVar>().toUInt()
    ) {
        return@memScoped false
    }
    value.value == 0u
}

/** System-wide DPI, read from the desktop device context. 96 is the 1.0 scale factor. */
@OptIn(ExperimentalForeignApi::class)
private fun systemDpi(): Int {
    val screen = GetDC(null) ?: return DEFAULT_DPI
    val dpi = GetDeviceCaps(screen, LOGPIXELSY)
    ReleaseDC(null, screen)
    return if (dpi > 0) dpi else DEFAULT_DPI
}

private const val DEFAULT_DPI = 96
