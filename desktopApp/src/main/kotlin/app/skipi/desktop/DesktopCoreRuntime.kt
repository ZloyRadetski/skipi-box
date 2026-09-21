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

    fun fromDirectory(directory: Path): Result<DesktopCoreRuntime> = runCatching {
        val runtimeDirectory = directory.toAbsolutePath().normalize()
        val library = runtimeDirectory.resolve(libraryFileName())
        val geoIp = runtimeDirectory.resolve("geoip.dat")
        val geoSite = runtimeDirectory.resolve("geosite.dat")
        val missing = listOf(library, geoIp, geoSite).filterNot(::isNonEmptyRegularFile)
        require(missing.isEmpty()) {
            "Incomplete SKIPI Core desktop runtime: ${missing.joinToString { it.fileName.toString() }}"
        }
        DesktopCoreRuntime(runtimeDirectory, library, geoIp, geoSite)
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

    private fun libraryFileName(): String = when {
        isWindows() -> "skipicore.dll"
        isLinux() -> "libskipicore.so"
        else -> error("SKIPI Core desktop runtime is currently available only on Windows and Linux")
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

    private fun isLinux(): Boolean =
        System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)

    private fun isNonEmptyRegularFile(path: Path): Boolean =
        Files.isRegularFile(path) && Files.size(path) > 0

    private fun applicationDirectoryOrNull(): Path? = runCatching {
        val codeSource = Path.of(DesktopCoreRuntimes::class.java.protectionDomain.codeSource.location.toURI())
        if (Files.isDirectory(codeSource)) codeSource else codeSource.parent?.parent
    }.getOrNull()
}
