import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKStringFromUtf16
import mingwresources.generated.resources.Res
import platform.windows.GetDC
import platform.windows.GetDeviceCaps
import platform.windows.GetUserDefaultLocaleName
import platform.windows.LOCALE_NAME_MAX_LENGTH
import platform.windows.LOGPIXELSY
import platform.windows.ReleaseDC
import platform.windows.WCHARVar

@OptIn(ExperimentalForeignApi::class)
suspend fun main() {
    check(Res.readBytes("drawable/common.svg").decodeToString().contains("#37BF6E")) {
        "The common resource has unexpected content"
    }
    check(Res.readBytes("drawable/windows.svg").decodeToString().contains("#3870B2")) {
        "The Windows resource has unexpected content"
    }
    check(Res.readBytes("files/device-🙂.txt").decodeToString().trim() == "unicode-path-ok") {
        "The Unicode-path resource has unexpected content"
    }
    check(Res.readBytes("files/platform-priority.txt").decodeToString().trim() == "windows") {
        "The mingwX64 resource did not override the common resource"
    }

    memScoped {
        val localeBuffer = allocArray<WCHARVar>(LOCALE_NAME_MAX_LENGTH)
        val localeLength = GetUserDefaultLocaleName(localeBuffer, LOCALE_NAME_MAX_LENGTH)
        check(localeLength > 1) { "GetUserDefaultLocaleName failed" }
        val locale = localeBuffer.toKStringFromUtf16()

        val screen = checkNotNull(GetDC(null))
        val dpi = GetDeviceCaps(screen, LOGPIXELSY)
        ReleaseDC(null, screen)
        check(dpi > 0) { "GetDeviceCaps(LOGPIXELSY) failed" }

        println("WINDOWS_DEVICE_RESOURCES_OK locale=$locale dpi=$dpi")
    }
}
