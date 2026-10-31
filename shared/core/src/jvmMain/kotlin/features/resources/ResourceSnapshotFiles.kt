// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile
import features.resources.runtime.openDecompressedStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/**
 * File operations bound to one captured profile scope.
 * Platform adapters supply [atomicWrite], which must atomically publish nonempty content and leave the previous target intact
 * on failure.
 */
class ResourceSnapshotFiles(
    private val xrayRootDir: File,
    private val scope: ResourceFileScope?,
    private val atomicWrite: (File, (OutputStream) -> Unit) -> Unit,
) {
    val dataDir: File = scope?.let { resourceScope ->
        File(File(xrayRootDir, ProfilesDirectoryName), resourceScope.trafficConfigId.toString())
    } ?: xrayRootDir

    fun file(kind: ResourceFileKind): File {
        val parent = if (kind == ResourceFileKind.XrayCore) xrayRootDir else dataDir
        return File(parent, kind.fileName)
    }

    fun file(customFile: TrafficConfigCustomResourceFile): File {
        return File(dataDir, resourceFileName(customFile))
    }

    fun currentStatus(
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ResourceFilesStatus {
        return ResourceFilesStatus(
            geoIp = file(ResourceFileKind.GeoIp).toStatus(),
            geoSite = file(ResourceFileKind.GeoSite).toStatus(),
            geoIpOnlyCnPrivate = file(ResourceFileKind.GeoIpOnlyCnPrivate).toStatus(),
            directCidrIpv4 = file(ResourceFileKind.DirectCidrIpv4).toStatus(),
            directCidrIpv6 = file(ResourceFileKind.DirectCidrIpv6).toStatus(),
            xrayCore = file(ResourceFileKind.XrayCore).toStatus(),
            customResourceFiles = customResourceFiles.map { customFile ->
                CustomResourceFileStatus(
                    file = customFile,
                    status = file(customFile).toStatus(),
                )
            },
        )
    }

    fun ensureBundledFiles(
        resourceFileSource: Int,
        bundledUpdatedAtMillis: Long,
        restoreAfterPackageUpdate: Boolean,
        openAsset: (String) -> InputStream?,
        onRestoreFailure: (ResourceFileKind, Throwable) -> Unit,
    ) {
        val sourceChanged = requiresBundledResourceSourceActivation(
            isProfileScoped = scope != null,
            storedSource = storedResourceFileSource(),
            requestedSource = resourceFileSource,
        )
        var allOk = true
        ResourceFileKind.entries.forEach { kind ->
            if (kind == ResourceFileKind.XrayCore) return@forEach
            val target = file(kind)
            if (
                !sourceChanged && !target.shouldRestoreBundled(
                    kind = kind,
                    resourceFileSource = resourceFileSource,
                    bundledUpdatedAtMillis = bundledUpdatedAtMillis,
                    restoreAfterPackageUpdate = restoreAfterPackageUpdate,
                )
            ) {
                return@forEach
            }
            // A kind the provider never ships has nothing to restore; failing here would keep the source
            // uncommitted forever and re-restore (and overwrite) every file on each preparation.
            if (!kind.hasBundledAsset(resourceFileSource)) return@forEach
            try {
                restoreBundled(kind, resourceFileSource, openAsset)
            } catch (error: Throwable) {
                allOk = false
                onRestoreFailure(kind, error)
            }
        }
        if (allOk) {
            markResourceFileSource(resourceFileSource)
        }
    }

    fun restoreBundled(
        kind: ResourceFileKind,
        resourceFileSource: Int,
        openAsset: (String) -> InputStream?,
    ) {
        require(kind != ResourceFileKind.XrayCore) { "Xray core must be restored through the locked publisher" }
        val assetPath = kind.bundledAssetPathOrNull(resourceFileSource)
            ?: kind.bundledAssetPathOrNull(ResourceFileSourceLoyalsoldierGithub)
            ?: error("Bundled ${kind.fileName} is unavailable")
        val (rawStream, resolvedPath) = openBundledAssetStream(assetPath, kind.fileName, openAsset)
        rawStream.use { rawInput ->
            openDecompressedStream(rawInput, resolvedPath).use { decompressedInput ->
                dataDir.mkdirs()
                atomicWrite(file(kind)) { output -> decompressedInput.copyTo(output) }
            }
        }
    }

    /**
     * Migrates the pre-profile shared directory into the first opened profile once.
     * The profile source marker is written before the global migration marker to preserve
     * the existing retry behavior if publishing the global marker fails.
     */
    fun migrateLegacyFilesIfNeeded(resourceFileSource: Int) {
        if (scope == null || sourceMarkerFile().isFile || legacyMigrationMarkerFile().isFile) return
        val migrationMarker = legacyMigrationMarkerFile()
        synchronized(migrationLockFor(migrationMarker)) {
            if (sourceMarkerFile().isFile || migrationMarker.isFile) return
            dataDir.mkdirs()
            xrayRootDir.listFiles()
                .orEmpty()
                .asSequence()
                .filter { legacyFile ->
                    legacyFile.isFile &&
                        legacyFile.name != ResourceFileKind.XrayCore.fileName &&
                        !legacyFile.name.startsWith('.')
                }
                .forEach { legacyFile ->
                    val target = File(dataDir, legacyFile.name)
                    if (!target.isFile || target.length() <= 0) {
                        legacyFile.inputStream().use { input ->
                            atomicWrite(target) { output -> input.copyTo(output) }
                        }
                    }
                }
            markResourceFileSource(resourceFileSource)
            atomicWrite(migrationMarker) { output ->
                output.write(LegacyMigrationMarkerValue.toByteArray(Charsets.UTF_8))
            }
        }
    }

    fun markResourceFileSource(resourceFileSource: Int) {
        if (scope == null) return
        atomicWrite(sourceMarkerFile()) { output ->
            output.write(resourceFileSource.toString().toByteArray(Charsets.UTF_8))
        }
    }

    fun replace(kind: ResourceFileKind, openInput: () -> InputStream) {
        require(kind != ResourceFileKind.XrayCore) { "Xray core must be replaced through the locked publisher" }
        replace(file(kind), openInput)
    }

    fun replaceCustom(customFile: TrafficConfigCustomResourceFile, openInput: () -> InputStream) {
        val target = file(customFile)
        if (isReservedName(target)) return
        replace(target, openInput)
    }

    fun deleteCustom(customFile: TrafficConfigCustomResourceFile) {
        val target = file(customFile)
        if (isReservedName(target)) return
        target.delete()
    }

    fun renameCustom(
        previousFile: TrafficConfigCustomResourceFile,
        customFile: TrafficConfigCustomResourceFile,
    ) {
        val source = file(previousFile)
        val target = file(customFile)
        if (isReservedName(source) || isReservedName(target)) return
        if (source.absolutePath == target.absolutePath) return
        if (!source.isFile) return

        dataDir.mkdirs()
        if (target.exists()) {
            target.delete()
        }
        if (!source.renameTo(target)) {
            source.inputStream().use { input ->
                atomicWrite(target) { output -> input.copyTo(output) }
            }
            source.delete()
        }
    }

    private fun replace(target: File, openInput: () -> InputStream) {
        dataDir.mkdirs()
        val replaceTempFile = target.resolveSibling("${target.name}.replace.tmp")
        openInput().use { input ->
            replaceTempFile.outputStream().use { output -> input.copyTo(output) }
        }
        if (replaceTempFile.length() <= 0) {
            replaceTempFile.delete()
            error("${target.name} is empty")
        }
        try {
            replaceTempFile.inputStream().use { input ->
                atomicWrite(target) { output -> input.copyTo(output) }
            }
        } finally {
            replaceTempFile.delete()
        }
    }

    private fun isReservedName(file: File): Boolean {
        return ResourceFileKind.entries.any { kind -> kind.fileName == file.name }
    }

    private fun storedResourceFileSource(): Int? {
        return sourceMarkerFile()
            .takeIf(File::isFile)
            ?.readText()
            ?.trim()
            ?.toIntOrNull()
    }

    private fun sourceMarkerFile(): File = File(dataDir, ResourceSourceMarkerFileName)

    private fun legacyMigrationMarkerFile(): File = File(xrayRootDir, LegacyMigrationMarkerFileName)

    private fun openBundledAssetStream(
        assetPath: String,
        fallbackFileName: String,
        openAsset: (String) -> InputStream?,
    ): Pair<InputStream, String> {
        val candidates = listOf(
            "$assetPath.xz",
            assetPath,
            "$fallbackFileName.xz",
            fallbackFileName,
        )
        for (candidate in candidates) {
            val stream = runCatching { openAsset(candidate) }.getOrNull()
            if (stream != null) {
                return stream to candidate
            }
        }
        throw FileNotFoundException("Bundled asset unavailable: $assetPath (fallback: $fallbackFileName)")
    }

    private fun ResourceFileKind.hasBundledAsset(resourceFileSource: Int): Boolean {
        return bundledAssetPathOrNull(resourceFileSource) != null
    }

    private fun ResourceFileKind.bundledAssetPathOrNull(resourceFileSource: Int): String? {
        val providerDir = resourceFileSourceAssetDir(resourceFileSource)
        return when (this) {
            ResourceFileKind.GeoIp,
            ResourceFileKind.GeoSite ->
                if (providerDir != null) "$providerDir/$fileName" else fileName
            // Only the v2fly bundle ships the CN/private list.
            ResourceFileKind.GeoIpOnlyCnPrivate -> when {
                providerDir == null -> fileName
                resourceFileSource == ResourceFileSourceV2FlyGithub -> "$providerDir/$fileName"
                else -> null
            }
            ResourceFileKind.DirectCidrIpv4,
            ResourceFileKind.DirectCidrIpv6 ->
                "$XrayBundledResourceFilesDir/$fileName"
            ResourceFileKind.XrayCore -> error("Xray core is restored from native libraries")
        }
    }

    private fun File.shouldRestoreBundled(
        kind: ResourceFileKind,
        resourceFileSource: Int,
        bundledUpdatedAtMillis: Long,
        restoreAfterPackageUpdate: Boolean,
    ): Boolean {
        return shouldRestoreBundledResourceFile(
            kind = kind,
            resourceFileSource = resourceFileSource,
            targetExists = exists(),
            targetLength = takeIf { exists() }?.length() ?: 0L,
            targetLastModifiedMillis = takeIf { exists() }?.lastModified() ?: 0L,
            bundledUpdatedAtMillis = bundledUpdatedAtMillis,
            restoreAfterPackageUpdate = restoreAfterPackageUpdate,
        )
    }

    private fun migrationLockFor(marker: File): Any {
        return synchronized(MigrationLocks) {
            MigrationLocks.getOrPut(marker.absolutePath) { Any() }
        }
    }

    private fun File.toStatus(): ResourceFileStatus {
        return ResourceFileStatus(
            exists = exists() && length() > 0,
            sizeBytes = takeIf { exists() }?.length() ?: 0,
            updatedAtMillis = takeIf { exists() }?.lastModified() ?: 0,
        )
    }

    private companion object {
        const val ProfilesDirectoryName = "profiles"
        const val XrayBundledResourceFilesDir = "xray"
        const val ResourceSourceMarkerFileName = ".resource-source"
        const val LegacyMigrationMarkerFileName = ".profile-resources-migrated-v1"
        const val LegacyMigrationMarkerValue = "done"
        val MigrationLocks = mutableMapOf<String, Any>()
    }
}
