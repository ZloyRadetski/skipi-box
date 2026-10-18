// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile
import features.config.sanitizeCustomResourceFileName
import java.io.File
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
        return File(
            dataDir,
            sanitizeCustomResourceFileName(
                value = customFile.name,
                fallback = "custom-resource-${customFile.id}.dat",
            ),
        )
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

    private fun File.toStatus(): ResourceFileStatus {
        return ResourceFileStatus(
            exists = exists() && length() > 0,
            sizeBytes = takeIf { exists() }?.length() ?: 0,
            updatedAtMillis = takeIf { exists() }?.lastModified() ?: 0,
        )
    }

    private companion object {
        const val ProfilesDirectoryName = "profiles"
    }
}
