// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import android.content.Context
import android.net.Uri
import app.CustomResourceFileState
import app.ResourceFileKind
import app.ResourceFilesStatus
import app.ResourceFileUpdateSource
import features.resources.runtime.AndroidResourceFileRepository
import features.resources.runtime.XrayResourceFileScope

internal class ResourceFileUseCase(
    context: Context,
    private val resourceFilePicker: suspend () -> Uri?,
) {
    private val repository = AndroidResourceFileRepository(context.applicationContext)

    suspend fun status(
        scope: XrayResourceFileScope,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
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
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus {
        return repository.update(scope, source, options, customResourceFiles)
    }

    suspend fun update(
        scope: XrayResourceFileScope,
        kind: ResourceFileKind,
        source: ResourceFileUpdateSource,
        options: ResourceFileUpdateOptions = ResourceFileUpdateOptions(),
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus {
        return repository.update(scope, kind, source, options, customResourceFiles)
    }

    suspend fun updateCustom(
        scope: XrayResourceFileScope,
        customFile: CustomResourceFileState,
        options: ResourceFileUpdateOptions = ResourceFileUpdateOptions(),
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus {
        return repository.updateCustom(scope, customFile, options, customResourceFiles)
    }

    suspend fun renameCustom(
        scope: XrayResourceFileScope,
        previousFile: CustomResourceFileState,
        customFile: CustomResourceFileState,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus {
        return repository.renameCustom(scope, previousFile, customFile, customResourceFiles)
    }

    suspend fun replace(
        scope: XrayResourceFileScope,
        kind: ResourceFileKind,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus? {
        val uri = resourceFilePicker() ?: return null
        return repository.replace(scope, kind, uri, customResourceFiles)
    }

    suspend fun replaceCustom(
        scope: XrayResourceFileScope,
        customFile: CustomResourceFileState,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus? {
        val uri = resourceFilePicker() ?: return null
        return repository.replaceCustom(scope, customFile, uri, customResourceFiles)
    }

    suspend fun restoreBundled(
        scope: XrayResourceFileScope,
        kind: ResourceFileKind,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus {
        return repository.restoreBundled(scope, kind, customResourceFiles)
    }

    suspend fun deleteCustom(
        scope: XrayResourceFileScope,
        customFile: CustomResourceFileState,
        customResourceFiles: List<CustomResourceFileState> = emptyList(),
    ): ResourceFilesStatus {
        return repository.deleteCustom(scope, customFile, customResourceFiles)
    }
}

data class ResourceFileUpdateOptions(
    val useRunningProxy: Boolean = false,
    val fallbackProxyPort: Int? = null,
    val fallbackProxyUsername: String = "",
    val fallbackProxyPassword: String = "",
    val userAgent: String = "",
    /** Whether system notifications should be shown while files are updated. */
    val showNotifications: Boolean = true,
)
