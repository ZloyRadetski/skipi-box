// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path

data class DesktopXrayRuntime(
    val directory: Path,
    val executable: Path,
    val geoIp: Path,
    val geoSite: Path,
)

/** Finds only the Xray runtime packaged by the Desktop distribution task. */
object DesktopXrayRuntimes {
    private const val RuntimeDirectoryProperty = "skipi.xray.runtime.dir"

    fun discover(): Result<DesktopXrayRuntime> = runCatching {
        val candidates = buildList {
            System.getProperty(RuntimeDirectoryProperty)
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?.let(::add)
            applicationDirectoryOrNull()?.resolve("resources")?.let(::add)
            Path.of(System.getProperty("user.dir"), "resources").let(::add)
        }.distinct()
        candidates.firstNotNullOfOrNull { directory -> fromDirectory(directory).getOrNull() }
            ?: error("SKIPI Xray runtime was not found in the packaged application resources")
    }

    fun fromDirectory(directory: Path): Result<DesktopXrayRuntime> = runCatching {
        val runtimeDirectory = directory.toAbsolutePath().normalize()
        val executable = runtimeDirectory.resolve("xray.exe")
        val geoIp = runtimeDirectory.resolve("geoip.dat")
        val geoSite = runtimeDirectory.resolve("geosite.dat")
        val missing = listOf(executable, geoIp, geoSite).filterNot { path ->
            Files.isRegularFile(path) && Files.size(path) > 0
        }
        require(missing.isEmpty()) {
            "Incomplete SKIPI Xray runtime: ${missing.joinToString { it.fileName.toString() }}"
        }
        DesktopXrayRuntime(runtimeDirectory, executable, geoIp, geoSite)
    }

    private fun applicationDirectoryOrNull(): Path? = runCatching {
        val codeSource = Path.of(DesktopXrayRuntimes::class.java.protectionDomain.codeSource.location.toURI())
        if (Files.isDirectory(codeSource)) codeSource else codeSource.parent?.parent
    }.getOrNull()
}
