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
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")
        val exeName = if (isWindows) "xray.exe" else "xray"
        val candidates = buildList {
            System.getProperty(RuntimeDirectoryProperty)
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?.let(::add)
            applicationDirectoryOrNull()?.resolve("resources")?.let(::add)
            Path.of(System.getProperty("user.dir"), "resources").let(::add)
            if (!isWindows) {
                Path.of("/usr/local/share/xray").let(::add)
                Path.of("/usr/share/xray").let(::add)
            }
        }.distinct()

        val direct = candidates.firstNotNullOfOrNull { directory -> fromDirectory(directory).getOrNull() }
        if (direct != null) return@runCatching direct

        val executable = findExecutable(candidates, exeName)
            ?: error("SKIPI Xray executable ($exeName) was not found in application resources or system PATH")
        val geoAssets = findGeoAssets(candidates, executable)
            ?: error("SKIPI Xray geo assets (geoip.dat, geosite.dat) were not found")

        val workingDir = geoAssets.first.parent ?: executable.parent ?: Path.of(System.getProperty("user.dir"))
        DesktopXrayRuntime(workingDir, executable, geoAssets.first, geoAssets.second)
    }

    private fun findExecutable(candidateDirs: List<Path>, exeName: String): Path? {
        for (dir in candidateDirs) {
            val file = dir.resolve(exeName)
            if (Files.isRegularFile(file) && Files.isExecutable(file)) return file
        }
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")
        val systemLocations = if (!isWindows) {
            listOf(
                Path.of("/usr/local/bin", exeName),
                Path.of("/usr/bin", exeName),
                Path.of("/bin", exeName),
                Path.of(System.getProperty("user.home"), ".local/bin", exeName),
            )
        } else emptyList()

        for (loc in systemLocations) {
            if (Files.isRegularFile(loc) && Files.isExecutable(loc)) return loc
        }

        val pathEnv = System.getenv("PATH").orEmpty()
        for (part in pathEnv.split(java.io.File.pathSeparator)) {
            if (part.isBlank()) continue
            val file = Path.of(part, exeName)
            if (Files.isRegularFile(file) && Files.isExecutable(file)) return file
        }
        return null
    }

    private fun findGeoAssets(candidateDirs: List<Path>, executable: Path): Pair<Path, Path>? {
        val geoDirs = buildList {
            executable.parent?.let(::add)
            addAll(candidateDirs)
            add(Path.of("/usr/local/share/xray"))
            add(Path.of("/usr/share/xray"))
            add(Path.of(System.getProperty("user.dir"), "app", "src", "main", "assets"))
            add(Path.of(System.getProperty("user.dir"), "app", "src", "main", "assets", "geo", "v2fly"))
            add(Path.of(System.getProperty("user.home"), ".local", "share", "xray"))
        }.distinct()

        for (dir in geoDirs) {
            val geoIp = dir.resolve("geoip.dat")
            val geoSite = dir.resolve("geosite.dat")
            if (Files.isRegularFile(geoIp) && Files.size(geoIp) > 0 &&
                Files.isRegularFile(geoSite) && Files.size(geoSite) > 0
            ) {
                return Pair(geoIp, geoSite)
            }
        }
        return null
    }

    fun fromDirectory(directory: Path): Result<DesktopXrayRuntime> = runCatching {
        val runtimeDirectory = directory.toAbsolutePath().normalize()
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("windows")
        val executable = when {
            Files.isRegularFile(runtimeDirectory.resolve("xray.exe")) -> runtimeDirectory.resolve("xray.exe")
            Files.isRegularFile(runtimeDirectory.resolve("xray")) -> runtimeDirectory.resolve("xray")
            else -> runtimeDirectory.resolve(if (isWindows) "xray.exe" else "xray")
        }
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
