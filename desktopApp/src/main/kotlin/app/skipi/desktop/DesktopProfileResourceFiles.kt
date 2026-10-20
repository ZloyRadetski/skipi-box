// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigState
import features.resources.ResourceFileKind
import features.resources.ResourceFileScope
import features.resources.ResourceSnapshotFiles
import features.resources.runtime.withResourceFileWriteLock
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal class DesktopProfileResourceFiles(
    private val xrayRootDir: File = DesktopSettingsLibraries.defaultPath().parent
        ?.resolve("xray")
        ?.toFile()
        ?: error("Desktop settings path must have a parent"),
) {
    fun snapshot(profile: TrafficConfigState): ResourceSnapshotFiles {
        return snapshot(
            ResourceFileScope(
                trafficConfigId = profile.id,
                resourceFileSource = profile.resourceSettings.source,
            ),
        )
    }

    fun snapshot(scope: ResourceFileScope): ResourceSnapshotFiles {
        return ResourceSnapshotFiles(
            xrayRootDir = xrayRootDir,
            scope = scope,
            atomicWrite = ::writeAtomically,
        )
    }

    fun createDownloader(): DesktopResourceFileDownloader = DesktopResourceFileDownloader(::writeAtomically)

    fun restoreBundled(scope: ResourceFileScope, kind: ResourceFileKind) {
        snapshot(scope).restoreBundled(
            kind = kind,
            resourceFileSource = scope.resourceFileSource,
            openAsset = ::openBundledAsset,
        )
    }

    fun prepare(
        profile: TrafficConfigState,
        bundledUpdatedAtMillis: Long,
        restoreAfterPackageUpdate: Boolean = false,
    ): ResourceSnapshotFiles = prepare(
        scope = ResourceFileScope(
            trafficConfigId = profile.id,
            resourceFileSource = profile.resourceSettings.source,
        ),
        bundledUpdatedAtMillis = bundledUpdatedAtMillis,
        restoreAfterPackageUpdate = restoreAfterPackageUpdate,
    )

    fun prepare(
        scope: ResourceFileScope,
        bundledUpdatedAtMillis: Long,
        restoreAfterPackageUpdate: Boolean = false,
    ): ResourceSnapshotFiles {
        val snapshot = snapshot(scope)
        snapshot.dataDir.mkdirs()
        snapshot.migrateLegacyFilesIfNeeded(scope.resourceFileSource)
        snapshot.ensureBundledFiles(
            resourceFileSource = scope.resourceFileSource,
            bundledUpdatedAtMillis = bundledUpdatedAtMillis,
            restoreAfterPackageUpdate = restoreAfterPackageUpdate,
            openAsset = ::openBundledAsset,
            onRestoreFailure = { kind, error ->
                DesktopLogger.warn(
                    "ResourceFiles",
                    "Failed to restore bundled resource file: ${kind.fileName}",
                    error,
                )
            },
        )
        return snapshot
    }

    private fun openBundledAsset(path: String): InputStream? {
        return DesktopProfileResourceFiles::class.java.classLoader?.getResourceAsStream(path)
    }

    private fun writeAtomically(target: File, write: (OutputStream) -> Unit) {
        val parent = target.parentFile ?: error("Parent directory is unavailable for ${target.absolutePath}")
        Files.createDirectories(parent.toPath())
        withResourceFileWriteLock(target) {
            val prefix = "${target.name}.".let { value ->
                if (value.length >= 3) value else value.padEnd(3, '_')
            }
            val temporary = File.createTempFile(prefix, ".tmp", parent)
            try {
                temporary.outputStream().use(write)
                if (temporary.length() <= 0) {
                    error("${target.name} is empty")
                }
                Files.move(temporary.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
    }
}
