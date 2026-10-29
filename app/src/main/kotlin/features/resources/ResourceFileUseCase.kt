// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import android.content.Context
import android.net.Uri
import features.config.TrafficConfigCustomResourceFile
import features.resources.ResourceFileKind
import features.resources.ResourceFilesStatus
import features.resources.ResourceFileUpdateSource
import features.resources.runtime.AndroidResourceFileRepository
import features.resources.runtime.XrayResourceFileScope
import features.resources.ResourceFileOperations

internal class ResourceFileUseCase(
    context: Context,
    private val resourceFilePicker: suspend () -> Uri?,
) : ResourceFileOperations {
    private val repository = AndroidResourceFileRepository(context.applicationContext)

    override suspend fun status(
        scope: XrayResourceFileScope,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus {
        return repository.status(scope, customResourceFiles)
    }

    suspend fun synchronizeBundledFilesAfterPackageUpdate(scope: XrayResourceFileScope) {
        repository.synchronizeBundledFilesAfterPackageUpdate(scope)
    }

    suspend fun update(
        scope: XrayResourceFileScope,
        source: ResourceFileUpdateSource,
        options: ResourceFileUpdateOptions = ResourceFileUpdateOptions(),
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ResourceFilesStatus {
        return repository.update(scope, source, options, customResourceFiles)
    }

    suspend fun update(
        scope: XrayResourceFileScope,
        kind: ResourceFileKind,
        source: ResourceFileUpdateSource,
        options: ResourceFileUpdateOptions = ResourceFileUpdateOptions(),
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ResourceFilesStatus {
        return repository.update(scope, kind, source, options, customResourceFiles)
    }

    suspend fun updateCustom(
        scope: XrayResourceFileScope,
        customFile: TrafficConfigCustomResourceFile,
        options: ResourceFileUpdateOptions = ResourceFileUpdateOptions(),
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ResourceFilesStatus {
        return repository.updateCustom(scope, customFile, options, customResourceFiles)
    }

    override suspend fun renameCustom(
        scope: XrayResourceFileScope,
        previousFile: TrafficConfigCustomResourceFile,
        customFile: TrafficConfigCustomResourceFile,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus {
        return repository.renameCustom(scope, previousFile, customFile, customResourceFiles)
    }

    override suspend fun replace(
        scope: XrayResourceFileScope,
        kind: ResourceFileKind,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus? {
        val uri = resourceFilePicker() ?: return null
        return repository.replace(scope, kind, uri, customResourceFiles)
    }

    override suspend fun replaceCustom(
        scope: XrayResourceFileScope,
        customFile: TrafficConfigCustomResourceFile,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus? {
        val uri = resourceFilePicker() ?: return null
        return repository.replaceCustom(scope, customFile, uri, customResourceFiles)
    }

    override suspend fun restoreBundled(
        scope: XrayResourceFileScope,
        kind: ResourceFileKind,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus {
        return repository.restoreBundled(scope, kind, customResourceFiles)
    }

    override suspend fun deleteCustom(
        scope: XrayResourceFileScope,
        customFile: TrafficConfigCustomResourceFile,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ): ResourceFilesStatus {
        return repository.deleteCustom(scope, customFile, customResourceFiles)
    }
}
