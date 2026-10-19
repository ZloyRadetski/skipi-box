// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import app.CustomResourceFileState
import app.ResourceFilesStatus
import features.resources.ResourceFileKind
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceSnapshotFiles
import features.resources.shouldRestoreBundledResourceFile
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipInputStream

internal class AndroidResourceFileStore(
    context: Context,
    private val scope: XrayResourceFileScope? = null,
) {
    private val appContext = context.applicationContext
    private val legacyDataDir: File = appContext.xrayResourceFilesDir()
    private val snapshotFiles = ResourceSnapshotFiles(
        xrayRootDir = legacyDataDir,
        scope = scope,
        atomicWrite = ::writeAtomically,
    )
    val dataDir: File
        get() = snapshotFiles.dataDir

    fun status(customResourceFiles: List<CustomResourceFileState> = emptyList()): ResourceFilesStatus {
        return currentStatus(customResourceFiles)
    }

    fun currentStatus(customResourceFiles: List<CustomResourceFileState> = emptyList()): ResourceFilesStatus {
        return snapshotFiles.currentStatus(customResourceFiles)
    }

    fun file(kind: ResourceFileKind): File {
        return snapshotFiles.file(kind)
    }

    fun file(customFile: CustomResourceFileState): File {
        return snapshotFiles.file(customFile)
    }

    fun synchronizeBundledFilesAfterPackageUpdate(resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub) {
        snapshotFiles.migrateLegacyFilesIfNeeded(resourceFileSource)
        ensureBundledFiles(
            resourceFileSource = resourceFileSource,
            restoreAfterPackageUpdate = true,
        )
    }

    fun ensureBundledFiles(
        resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub,
        restoreAfterPackageUpdate: Boolean = false,
    ) {
        snapshotFiles.ensureBundledFiles(
            resourceFileSource = resourceFileSource,
            bundledUpdatedAtMillis = appContext.packageUpdatedAtMillis(),
            restoreAfterPackageUpdate = restoreAfterPackageUpdate,
            openAsset = { path -> appContext.assets.open(path) },
            onRestoreFailure = { kind, error ->
                AndroidResourceFileLogger.warn(
                    "Failed to restore bundled resource file: ${kind.fileName}",
                    error,
                )
            },
        )
    }

    fun restoreBundled(
        kind: ResourceFileKind,
        resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub,
    ) {
        snapshotFiles.restoreBundled(
            kind = kind,
            resourceFileSource = resourceFileSource,
            openAsset = { path -> appContext.assets.open(path) },
        )
        kind.applyPermissions(file(kind))
    }

    fun stageBundledXrayCoreCandidate(): File {
        val source = bundledXrayCoreFileOrNull()
            ?: error("Bundled ${ResourceFileKind.XrayCore.fileName} is not available for ${currentRuntimeAbi()}")
        return source.inputStream().use(::writeXrayCoreCandidate)
    }

    fun installInitialXrayCoreCandidate(candidate: File): Boolean {
        require(candidate.isFile && candidate.length() > 0) { "Xray core candidate is empty" }
        require(dataDir.exists() || dataDir.mkdirs()) { "Failed to create ${dataDir.absolutePath}" }
        val target = file(ResourceFileKind.XrayCore)
        return withResourceFileWriteLock(target) {
            if (target.exists()) return@withResourceFileWriteLock false
            val temp = File.createTempFile(".xray-initial-", ".tmp", dataDir)
            try {
                candidate.inputStream().use { input ->
                    temp.outputStream().use { output ->
                        input.copyTo(output)
                        output.flush()
                        output.fd.sync()
                    }
                }
                require(temp.length() > 0) { "Xray core candidate is empty" }
                Os.chmod(temp.absolutePath, XrayExecutableMode)
                try {
                    Os.link(temp.absolutePath, target.absolutePath)
                } catch (error: ErrnoException) {
                    if (error.errno == OsConstants.EEXIST) return@withResourceFileWriteLock false
                    throw error
                }
                syncDirectory(dataDir)
                true
            } finally {
                temp.delete()
            }
        }
    }

    fun shouldPublishBundledXrayCore(
        resourceFileSource: Int,
        restoreAfterPackageUpdate: Boolean,
    ): Boolean {
        val bundledUpdatedAtMillis = appContext.packageUpdatedAtMillis()
        return bundledXrayCoreFileOrNull() != null && file(ResourceFileKind.XrayCore).shouldRestoreBundled(
            kind = ResourceFileKind.XrayCore,
            resourceFileSource = resourceFileSource,
            bundledUpdatedAtMillis = bundledUpdatedAtMillis,
            restoreAfterPackageUpdate = restoreAfterPackageUpdate,
        )
    }

    private fun bundledXrayCoreFileOrNull(): File? {
        if (currentRuntimeAbi() != Arm64Abi) return null
        return File(appContext.applicationInfo.nativeLibraryDir, XrayCoreLibraryName)
            .takeIf { it.isFile && it.length() > 0 }
    }

    fun replace(kind: ResourceFileKind, uri: Uri) {
        snapshotFiles.replace(kind) {
            appContext.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        }
        kind.applyPermissions(file(kind))
    }

    fun stageXrayCoreCandidate(uri: Uri): File {
        val uploaded = appContext.contentResolver.openInputStream(uri)?.use(::writeXrayCoreCandidate)
            ?: throw FileNotFoundException(uri.toString())
        val extracted = createXrayCoreCandidateFile()
        val found = runCatching {
            ZipInputStream(uploaded.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.substringAfterLast('/') == "xray") {
                        extracted.outputStream().use { output ->
                            zip.copyTo(output)
                            output.flush()
                            output.fd.sync()
                        }
                        return@runCatching extracted.length() > 0
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                false
            }
        }.getOrDefault(false)
        return if (found) {
            uploaded.delete()
            extracted
        } else {
            extracted.delete()
            uploaded
        }
    }

    private fun writeXrayCoreCandidate(input: java.io.InputStream): File {
        val candidate = createXrayCoreCandidateFile()
        try {
            candidate.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
            require(candidate.length() > 0) { "Xray core candidate is empty" }
            return candidate
        } catch (error: Throwable) {
            candidate.delete()
            throw error
        }
    }

    private fun createXrayCoreCandidateFile(): File {
        require(appContext.cacheDir.exists() || appContext.cacheDir.mkdirs())
        return File.createTempFile("xray-core-", ".candidate", appContext.cacheDir)
    }

    fun replaceCustom(customFile: CustomResourceFileState, uri: Uri) {
        snapshotFiles.replaceCustom(customFile) {
            appContext.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        }
    }

    fun applyPermissions(kind: ResourceFileKind) {
        kind.applyPermissions(file(kind))
    }

    /** Records that this profile snapshot now contains files for [resourceFileSource]. */
    fun markResourceFileSource(resourceFileSource: Int) {
        snapshotFiles.markResourceFileSource(resourceFileSource)
    }

    fun deleteCustom(customFile: CustomResourceFileState) {
        snapshotFiles.deleteCustom(customFile)
    }

    fun renameCustom(previousFile: CustomResourceFileState, customFile: CustomResourceFileState) {
        snapshotFiles.renameCustom(previousFile, customFile)
    }

    fun preparePaths(
        resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub,
        restoreBundledFiles: Boolean = true,
    ): XrayResourceFilePaths {
        dataDir.mkdirs()
        snapshotFiles.migrateLegacyFilesIfNeeded(resourceFileSource)
        if (restoreBundledFiles) {
            ensureBundledFiles(resourceFileSource = resourceFileSource)
        }
        return currentPaths()
    }

    fun currentPaths(): XrayResourceFilePaths {
        return XrayResourceFilePaths(
            dataDir = dataDir.absolutePath,
            xrayCorePath = file(ResourceFileKind.XrayCore).absolutePath,
            hevSocks5TunnelPath = File(appContext.applicationInfo.nativeLibraryDir, HevSocks5TunnelLibraryName).absolutePath,
            directCidrIpv4Path = file(ResourceFileKind.DirectCidrIpv4).absolutePath,
            directCidrIpv6Path = file(ResourceFileKind.DirectCidrIpv6).absolutePath,
        )
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

internal data class XrayResourceFilePaths(
    val dataDir: String,
    val xrayCorePath: String,
    val hevSocks5TunnelPath: String,
    val directCidrIpv4Path: String,
    val directCidrIpv6Path: String,
)

/** Identifies one traffic profile's isolated Xray resource snapshot. */
internal typealias XrayResourceFileScope = features.resources.ResourceFileScope

internal fun Context.xrayResourceFilesDir(scope: XrayResourceFileScope? = null): File {
    return ResourceSnapshotFiles(
        xrayRootDir = File(filesDir, "xray"),
        scope = scope,
        atomicWrite = ::writeAtomically,
    ).dataDir
}

internal fun Context.prepareXrayResourceFilePaths(
    scope: XrayResourceFileScope? = null,
    restoreBundledFiles: Boolean = true,
): XrayResourceFilePaths {
    return AndroidResourceFileStore(this, scope).preparePaths(
        resourceFileSource = scope?.resourceFileSource ?: ResourceFileSourceLoyalsoldierGithub,
        restoreBundledFiles = restoreBundledFiles,
    )
}

internal fun Context.xrayResourceFilePaths(scope: XrayResourceFileScope? = null): XrayResourceFilePaths {
    return AndroidResourceFileStore(this, scope).currentPaths()
}

private fun currentRuntimeAbi(): String {
    return Build.SUPPORTED_ABIS.firstOrNull { abi -> abi in SupportedAndroidAbis }
        ?: error("Unsupported CPU ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
}

@Volatile
private var cachedPackageUpdatedAtMillis: Long? = null

private fun Context.packageUpdatedAtMillis(): Long {
    cachedPackageUpdatedAtMillis?.let { return it }
    return runCatching {
        val time = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                .lastUpdateTime
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        }
        if (time > 0) cachedPackageUpdatedAtMillis = time
        time
    }.getOrDefault(0L)
}

private const val Arm64Abi = "arm64-v8a"
private const val XrayCoreLibraryName = "libxray.so"
private const val HevSocks5TunnelLibraryName = "libhev-socks5-tunnel-cli.so"
private const val XrayExecutableMode = 493

private val SupportedAndroidAbis = setOf(Arm64Abi, "armeabi-v7a", "x86", "x86_64")


private fun syncDirectory(directory: File) {
    val descriptor = Os.open(
        directory.absolutePath,
        OsConstants.O_RDONLY,
        0,
    )
    try {
        Os.fsync(descriptor)
    } finally {
        Os.close(descriptor)
    }
}

internal fun writeAtomically(
    target: File,
    write: (java.io.OutputStream) -> Unit,
) {
    val parent = target.parentFile ?: error("Parent directory is unavailable for ${target.absolutePath}")
    parent.mkdirs()
    withResourceFileWriteLock(target) {
        val tempPrefix = "${target.name}.".let { prefix ->
            if (prefix.length >= 3) prefix else prefix.padEnd(3, '_')
        }
        val tempFile = File.createTempFile(tempPrefix, ".tmp", parent)
        try {
            tempFile.outputStream().use(write)
            if (tempFile.length() <= 0) {
                tempFile.delete()
                error("${target.name} is empty")
            }
            // POSIX rename replaces the old entry atomically.  Deleting the
            // target first made Xray occasionally observe a missing geo file
            // while a profile update was finishing.
            Os.rename(tempFile.absolutePath, target.absolutePath)
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
    }
}

private fun ResourceFileKind.applyPermissions(file: File) {
    if (this == ResourceFileKind.XrayCore) {
        file.setExecutable(true, false)
    }
}
