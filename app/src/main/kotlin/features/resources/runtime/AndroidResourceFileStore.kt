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
import app.CustomResourceFileStatus
import app.ResourceFileKind
import app.ResourceFileStatus
import app.ResourceFilesStatus
import app.sanitizeCustomResourceFileName
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.resourceFileSourceAssetDir
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipInputStream

internal class AndroidResourceFileStore(
    context: Context,
    private val scope: XrayResourceFileScope? = null,
) {
    private val appContext = context.applicationContext
    private val legacyDataDir: File = appContext.xrayResourceFilesDir()
    val dataDir: File = appContext.xrayResourceFilesDir(scope)

    fun status(customResourceFiles: List<CustomResourceFileState> = emptyList()): ResourceFilesStatus {
        return currentStatus(customResourceFiles)
    }

    fun currentStatus(customResourceFiles: List<CustomResourceFileState> = emptyList()): ResourceFilesStatus {
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

    fun file(kind: ResourceFileKind): File {
        val parent = if (kind == ResourceFileKind.XrayCore) legacyDataDir else dataDir
        return File(parent, kind.fileName)
    }

    fun file(customFile: CustomResourceFileState): File {
        return File(
            dataDir,
            sanitizeCustomResourceFileName(
                value = customFile.name,
                fallback = "custom-resource-${customFile.id}.dat",
            ),
        )
    }

    fun synchronizeBundledFilesAfterPackageUpdate(resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub) {
        migrateLegacyFilesIfNeeded(resourceFileSource)
        ensureBundledFiles(
            resourceFileSource = resourceFileSource,
            restoreAfterPackageUpdate = true,
        )
    }

    fun ensureBundledFiles(
        resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub,
        restoreAfterPackageUpdate: Boolean = false,
    ) {
        val sourceChanged = requiresBundledResourceSourceActivation(
            isProfileScoped = scope != null,
            storedSource = storedResourceFileSource(),
            requestedSource = resourceFileSource,
        )
        val bundledUpdatedAtMillis = appContext.packageUpdatedAtMillis()
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
            if (!sourceChanged && !kind.hasBundledAsset(resourceFileSource)) return@forEach
            runCatching { restoreBundled(kind, resourceFileSource) }
                .onFailure { error ->
                    allOk = false
                    AndroidResourceFileLogger.warn(
                        "Failed to restore bundled resource file: ${kind.fileName}",
                        error,
                    )
                }
        }
        if (allOk) {
            storeResourceFileSource(resourceFileSource)
        }
    }

    fun restoreBundled(
        kind: ResourceFileKind,
        resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub,
    ) {
        require(kind != ResourceFileKind.XrayCore) { "Xray core must be restored through the locked publisher" }
        val assetPath = kind.bundledAssetPathOrNull(resourceFileSource)
            ?: kind.bundledAssetPathOrNull(ResourceFileSourceLoyalsoldierGithub)
            ?: error("Bundled ${kind.fileName} is unavailable")
        restoreBundledAsset(kind, assetPath)
    }

    private fun restoreBundledAsset(kind: ResourceFileKind, assetPath: String) {
        val (rawStream, resolvedPath) = openBundledAssetStream(assetPath, kind.fileName)
        rawStream.use { rawInput ->
            openDecompressedStream(rawInput, resolvedPath).use { decompressedInput ->
                dataDir.mkdirs()
                writeAtomically(file(kind)) { output -> decompressedInput.copyTo(output) }
            }
        }
        kind.applyPermissions(file(kind))
    }

    private fun openBundledAssetStream(assetPath: String, fallbackFileName: String): Pair<java.io.InputStream, String> {
        val candidates = listOf(
            "$assetPath.xz",
            assetPath,
            "$fallbackFileName.xz",
            fallbackFileName,
        )
        for (candidate in candidates) {
            val stream = runCatching { appContext.assets.open(candidate) }.getOrNull()
            if (stream != null) {
                return stream to candidate
            }
        }
        throw FileNotFoundException("Bundled asset unavailable: $assetPath (fallback: $fallbackFileName)")
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
        return synchronized(writeLockFor(target)) {
            if (target.exists()) return@synchronized false
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
                    if (error.errno == OsConstants.EEXIST) return@synchronized false
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
        require(kind != ResourceFileKind.XrayCore) { "Xray core must be replaced through the locked publisher" }
        dataDir.mkdirs()
        val replaceTempFile = file(kind).resolveSibling("${kind.fileName}.replace.tmp")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            replaceTempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw FileNotFoundException(uri.toString())

        replaceFile(replaceTempFile, file(kind))
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
        val target = file(customFile)
        if (ResourceFileKind.entries.any { kind -> kind.fileName == target.name }) return
        dataDir.mkdirs()
        val replaceTempFile = target.resolveSibling("${target.name}.replace.tmp")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            replaceTempFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw FileNotFoundException(uri.toString())

        replaceFile(replaceTempFile, target)
    }

    fun applyPermissions(kind: ResourceFileKind) {
        kind.applyPermissions(file(kind))
    }

    /** Records that this profile snapshot now contains files for [resourceFileSource]. */
    fun markResourceFileSource(resourceFileSource: Int) {
        storeResourceFileSource(resourceFileSource)
    }

    fun deleteCustom(customFile: CustomResourceFileState) {
        val target = file(customFile)
        if (ResourceFileKind.entries.any { kind -> kind.fileName == target.name }) return
        target.delete()
    }

    fun renameCustom(previousFile: CustomResourceFileState, customFile: CustomResourceFileState) {
        val source = file(previousFile)
        val target = file(customFile)
        if (ResourceFileKind.entries.any { kind -> kind.fileName == source.name || kind.fileName == target.name }) return
        if (source.absolutePath == target.absolutePath) return
        if (!source.isFile) return

        dataDir.mkdirs()
        if (target.exists()) {
            target.delete()
        }
        if (!source.renameTo(target)) {
            source.inputStream().use { input ->
                writeAtomically(target) { output -> input.copyTo(output) }
            }
            source.delete()
        }
    }

    fun preparePaths(
        resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub,
        restoreBundledFiles: Boolean = true,
    ): XrayResourceFilePaths {
        dataDir.mkdirs()
        migrateLegacyFilesIfNeeded(resourceFileSource)
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

    /**
     * Old SKIPI versions kept every profile's files in one shared directory.
     * Move that snapshot only once, into whichever profile is actually opened
     * first after the migration. This preserves a user's downloaded data
     * without letting a later profile inherit a different provider's files.
     */
    private fun migrateLegacyFilesIfNeeded(resourceFileSource: Int) {
        if (scope == null || sourceMarkerFile().isFile || legacyMigrationMarkerFile().isFile) return
        synchronized(writeLockFor(legacyMigrationMarkerFile())) {
            if (sourceMarkerFile().isFile || legacyMigrationMarkerFile().isFile) return
            dataDir.mkdirs()
            legacyDataDir.listFiles()
                .orEmpty()
                .asSequence()
                .filter { file ->
                    file.isFile &&
                        file.name != ResourceFileKind.XrayCore.fileName &&
                        !file.name.startsWith('.')
                }
                .forEach { legacyFile ->
                    val target = File(dataDir, legacyFile.name)
                    if (!target.isFile || target.length() <= 0) {
                        legacyFile.inputStream().use { input ->
                            writeAtomically(target) { output -> input.copyTo(output) }
                        }
                    }
                }
            storeResourceFileSource(resourceFileSource)
            writeAtomically(legacyMigrationMarkerFile()) { output ->
                output.write(LegacyMigrationMarkerValue.toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun storedResourceFileSource(): Int? {
        return sourceMarkerFile()
            .takeIf(File::isFile)
            ?.readText()
            ?.trim()
            ?.toIntOrNull()
    }

    private fun storeResourceFileSource(resourceFileSource: Int) {
        if (scope == null) return
        writeAtomically(sourceMarkerFile()) { output ->
            output.write(resourceFileSource.toString().toByteArray(Charsets.UTF_8))
        }
    }

    private fun sourceMarkerFile(): File = File(dataDir, ResourceSourceMarkerFileName)

    private fun legacyMigrationMarkerFile(): File = File(legacyDataDir, LegacyMigrationMarkerFileName)
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

internal fun shouldRestoreBundledResourceFile(
    kind: ResourceFileKind,
    resourceFileSource: Int,
    targetExists: Boolean,
    targetLength: Long,
    targetLastModifiedMillis: Long,
    bundledUpdatedAtMillis: Long,
    restoreAfterPackageUpdate: Boolean,
): Boolean {
    if (!targetExists || targetLength <= 0) return true
    if (!restoreAfterPackageUpdate) return false
    if (kind != ResourceFileKind.XrayCore && resourceFileSource == ResourceFileSourceCustom) {
        return false
    }
    return bundledUpdatedAtMillis > 0 && targetLastModifiedMillis < bundledUpdatedAtMillis
}

internal data class XrayResourceFilePaths(
    val dataDir: String,
    val xrayCorePath: String,
    val hevSocks5TunnelPath: String,
    val directCidrIpv4Path: String,
    val directCidrIpv6Path: String,
)

/** Identifies one traffic profile's isolated Xray resource snapshot. */
internal data class XrayResourceFileScope(
    val trafficConfigId: Int,
    val resourceFileSource: Int,
) {
    init {
        require(trafficConfigId > 0) { "Traffic config id must be positive" }
    }
}

/** A profile must receive its own bundled snapshot when its selected provider changes. */
internal fun requiresBundledResourceSourceActivation(
    isProfileScoped: Boolean,
    storedSource: Int?,
    requestedSource: Int,
): Boolean = isProfileScoped && storedSource != requestedSource

internal fun Context.xrayResourceFilesDir(scope: XrayResourceFileScope? = null): File {
    val root = File(filesDir, "xray")
    return scope?.let { File(File(root, XrayResourceProfilesDirName), it.trafficConfigId.toString()) } ?: root
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

private fun ResourceFileKind.hasBundledAsset(resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub): Boolean {
    return this == ResourceFileKind.XrayCore || bundledAssetPathOrNull(resourceFileSource) != null
}

private fun ResourceFileKind.bundledAssetPathOrNull(resourceFileSource: Int = ResourceFileSourceLoyalsoldierGithub): String? {
    val providerDir = resourceFileSourceAssetDir(resourceFileSource)
    return when (this) {
        ResourceFileKind.GeoIp -> if (providerDir != null) "$providerDir/$fileName" else fileName
        ResourceFileKind.GeoSite -> if (providerDir != null) "$providerDir/$fileName" else fileName
        ResourceFileKind.GeoIpOnlyCnPrivate -> if (providerDir != null) "$providerDir/$fileName" else fileName
        ResourceFileKind.DirectCidrIpv4 -> "$XrayBundledResourceFilesDir/$fileName"
        ResourceFileKind.DirectCidrIpv6 -> "$XrayBundledResourceFilesDir/$fileName"
        ResourceFileKind.XrayCore -> error("Xray-core is restored from native libraries")
    }
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
private const val XrayBundledResourceFilesDir = "xray"
private const val XrayResourceProfilesDirName = "profiles"
private const val ResourceSourceMarkerFileName = ".resource-source"
private const val LegacyMigrationMarkerFileName = ".profile-resources-migrated-v1"
private const val LegacyMigrationMarkerValue = "done"
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

private fun File.toStatus(): ResourceFileStatus {
    return ResourceFileStatus(
        exists = exists() && length() > 0,
        sizeBytes = takeIf { exists() }?.length() ?: 0,
        updatedAtMillis = takeIf { exists() }?.lastModified() ?: 0,
    )
}

private fun replaceFile(source: File, target: File) {
    if (source.length() <= 0) {
        source.delete()
        error("${target.name} is empty")
    }
    try {
        source.inputStream().use { input ->
            writeAtomically(target) { output -> input.copyTo(output) }
        }
    } finally {
        source.delete()
    }
}

internal fun writeAtomically(
    target: File,
    write: (java.io.OutputStream) -> Unit,
) {
    val parent = target.parentFile ?: error("Parent directory is unavailable for ${target.absolutePath}")
    parent.mkdirs()
    synchronized(writeLockFor(target)) {
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

private val WriteLocks = mutableMapOf<String, Any>()

private fun writeLockFor(target: File): Any {
    return synchronized(WriteLocks) {
        WriteLocks.getOrPut(target.absolutePath) { Any() }
    }
}

private fun ResourceFileKind.applyPermissions(file: File) {
    if (this == ResourceFileKind.XrayCore) {
        file.setExecutable(true, false)
    }
}
