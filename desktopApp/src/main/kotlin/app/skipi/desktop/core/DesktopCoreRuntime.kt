// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path

/** Files packaged with the Desktop app for one in-process SKIPI Core runtime. */
data class DesktopCoreRuntime(
    val directory: Path,
    val library: Path,
    val geoIp: Path,
    val geoSite: Path,
    /** Present on Linux builds that ship the TUN mode. */
    val hevLibrary: Path? = null,
    /** Present on Windows and macOS builds that ship the TUN mode: hev-socks5-tunnel, run as its own process. */
    val hevExecutable: Path? = null,
)

/** Locates only a shared SKIPI Core library and its Xray geo resources. */
object DesktopCoreRuntimes {
    private const val RuntimeDirectoryProperty = "skipi.core.runtime.dir"
    private const val ComposeResourcesDirectoryProperty = "compose.application.resources.dir"

    fun discover(): Result<DesktopCoreRuntime> = runCatching {
        val runtime = candidateDirectories()
            .firstNotNullOfOrNull { directory -> fromDirectory(directory).getOrNull() }
        requireNotNull(runtime) {
            "SKIPI Core desktop runtime (${libraryFileName()}, geoip.dat, geosite.dat) " +
                "was not found in application resources"
        }
    }

    fun fromDirectory(
        directory: Path,
        osName: String = System.getProperty("os.name").orEmpty(),
    ): Result<DesktopCoreRuntime> = runCatching {
        val runtimeDirectory = directory.toAbsolutePath().normalize()
        val library = runtimeDirectory.resolve(libraryFileName(osName))
        val geoIp = runtimeDirectory.resolve("geoip.dat")
        val geoSite = runtimeDirectory.resolve("geosite.dat")
        val missing = listOf(library, geoIp, geoSite).filterNot(::isNonEmptyRegularFile)
        require(missing.isEmpty()) {
            "Incomplete SKIPI Core desktop runtime: ${missing.joinToString { it.fileName.toString() }}"
        }
        val hev = runtimeDirectory.resolve("libhev-socks5-tunnel.so").takeIf(::isNonEmptyRegularFile)
        val hevExecutable = if (osName.contains("mac", ignoreCase = true)) {
            macHevExecutable(runtimeDirectory)
        } else {
            windowsHevExecutable(runtimeDirectory)
        }
        DesktopCoreRuntime(runtimeDirectory, library, geoIp, geoSite, hev, hevExecutable)
    }

    private fun candidateDirectories(): List<Path> = buildList {
        System.getProperty(RuntimeDirectoryProperty)
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?.let(::add)
        System.getProperty(ComposeResourcesDirectoryProperty)
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?.let(::add)
        applicationDirectoryOrNull()?.resolve("resources")?.let(::add)
        Path.of(System.getProperty("user.dir"), "resources").let(::add)
        if (isLinux()) {
            Path.of("/usr/local/lib/skipi-core").let(::add)
            Path.of("/usr/local/share/skipi-core").let(::add)
            Path.of("/usr/share/skipi-core").let(::add)
        }
    }.distinct()

    /** hev needs Wintun and the MSYS runtime in its own directory, so all three must be there. */
    private fun windowsHevExecutable(runtimeDirectory: Path): Path? {
        val directory = runtimeDirectory.resolve("hev")
        val executable = directory.resolve("hev-socks5-tunnel.exe")
        val complete = listOf(executable, directory.resolve("wintun.dll"), directory.resolve("msys-2.0.dll"))
            .all(::isNonEmptyRegularFile)
        return executable.takeIf { complete }
    }

    /** macOS runs hev as a root process of its own, so the bare executable is enough. */
    private fun macHevExecutable(runtimeDirectory: Path): Path? =
        runtimeDirectory.resolve("hev").resolve("hev-socks5-tunnel").takeIf(::isNonEmptyRegularFile)

    private fun libraryFileName(osName: String = System.getProperty("os.name").orEmpty()): String = when {
        osName.contains("windows", ignoreCase = true) -> "skipicore.dll"
        osName.contains("linux", ignoreCase = true) -> "libskipicore.so"
        osName.contains("mac", ignoreCase = true) -> "libskipicore.dylib"
        else -> error("SKIPI Core desktop runtime is currently available only on Windows, Linux and macOS")
    }

    private fun isLinux(): Boolean =
        System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)

    private fun isNonEmptyRegularFile(path: Path): Boolean =
        Files.isRegularFile(path) && Files.size(path) > 0

    private fun applicationDirectoryOrNull(): Path? = runCatching {
        val codeSource = Path.of(DesktopCoreRuntimes::class.java.protectionDomain.codeSource.location.toURI())
        if (Files.isDirectory(codeSource)) codeSource else codeSource.parent?.parent
    }.getOrNull()
}
