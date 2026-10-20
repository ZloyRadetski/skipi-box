// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.customResourceFileNameOrNull

sealed interface ResourceFileNameValidation {
    data class Valid(val name: String) : ResourceFileNameValidation

    data object Invalid : ResourceFileNameValidation

    data object Duplicate : ResourceFileNameValidation
}

sealed interface ResourceFileEditorResult {
    data object Saved : ResourceFileEditorResult

    data class Rejected(val reason: ResourceFileNameValidation) : ResourceFileEditorResult
}

sealed interface ResourceFileEditorEffect {
    data class ReplaceCustom(
        val file: TrafficConfigCustomResourceFile,
        val customFiles: List<TrafficConfigCustomResourceFile>,
    ) : ResourceFileEditorEffect

    data class RenameCustom(
        val previousFile: TrafficConfigCustomResourceFile,
        val file: TrafficConfigCustomResourceFile,
        val customFiles: List<TrafficConfigCustomResourceFile>,
    ) : ResourceFileEditorEffect

    data class DeleteCustom(
        val file: TrafficConfigCustomResourceFile,
        val customFiles: List<TrafficConfigCustomResourceFile>,
    ) : ResourceFileEditorEffect
}

data class ResourceFileSourceDraft(
    val geoIpUrl: String,
    val geoSiteUrl: String,
    val geoIpOnlyCnPrivateUrl: String,
    val directCidrIpv4Url: String,
    val directCidrIpv6Url: String,
)

/** Validates an editor name against the generic filename rules and current reserved names. */
fun validateCustomResourceFileName(
    name: String,
    customFiles: List<TrafficConfigCustomResourceFile>,
    editingFileId: Int? = null,
): ResourceFileNameValidation {
    val fileName = customResourceFileNameOrNull(name)
        ?: return ResourceFileNameValidation.Invalid
    val reservedNames = ResourceFileKind.entries.mapTo(mutableSetOf()) { kind -> kind.fileName }
    customFiles
        .filterNot { file -> file.id == editingFileId }
        .forEach { file -> reservedNames += file.name }
    return if (fileName in reservedNames) {
        ResourceFileNameValidation.Duplicate
    } else {
        ResourceFileNameValidation.Valid(fileName)
    }
}

/** Creates the source editor's local draft using the selected provider for blank fields. */
fun beginCustomResourceFileSourceEdit(
    settings: TrafficConfigResourceSettings,
    resolvedSelectedSource: ResourceFileUpdateSource,
): ResourceFileSourceDraft = ResourceFileSourceDraft(
    geoIpUrl = settings.customGeoIpUrl.ifBlank { resolvedSelectedSource.geoIpUrl },
    geoSiteUrl = settings.customGeoSiteUrl.ifBlank { resolvedSelectedSource.geoSiteUrl },
    geoIpOnlyCnPrivateUrl = settings.customGeoIpOnlyCnPrivateUrl.ifBlank {
        resolvedSelectedSource.geoIpOnlyCnPrivateUrl
    },
    directCidrIpv4Url = settings.customDirectCidrIpv4Url.ifBlank {
        resolvedSelectedSource.directCidrIpv4Url
    },
    directCidrIpv6Url = settings.customDirectCidrIpv6Url.ifBlank {
        resolvedSelectedSource.directCidrIpv6Url
    },
)

/** Applies the source editor's first Save step; provider selection remains a separate commit. */
fun applyCustomResourceFileSourceDraft(
    settings: TrafficConfigResourceSettings,
    draft: ResourceFileSourceDraft,
    resolvedSelectedSource: ResourceFileUpdateSource,
): TrafficConfigResourceSettings = settings.copy(
    customGeoIpUrl = draft.geoIpUrl.trim().ifBlank { resolvedSelectedSource.geoIpUrl },
    customGeoSiteUrl = draft.geoSiteUrl.trim().ifBlank { resolvedSelectedSource.geoSiteUrl },
    customGeoIpOnlyCnPrivateUrl = draft.geoIpOnlyCnPrivateUrl.trim().ifBlank {
        resolvedSelectedSource.geoIpOnlyCnPrivateUrl
    },
    customDirectCidrIpv4Url = draft.directCidrIpv4Url.trim().ifBlank {
        resolvedSelectedSource.directCidrIpv4Url
    },
    customDirectCidrIpv6Url = draft.directCidrIpv6Url.trim().ifBlank {
        resolvedSelectedSource.directCidrIpv6Url
    },
)

/** Changes only the selected source id, preserving the independently stored custom URLs. */
fun selectResourceFileSource(
    settings: TrafficConfigResourceSettings,
    sourceId: Int,
): TrafficConfigResourceSettings = settings.copy(source = sourceId)

/** Adds metadata using the latest settings supplied by the host, then emits any storage effect. */
fun addCustomResourceFile(
    displayedSettings: TrafficConfigResourceSettings,
    name: String,
    url: String,
    updateSettings: ((TrafficConfigResourceSettings) -> TrafficConfigResourceSettings) -> Unit,
    afterCommit: (ResourceFileEditorEffect) -> Unit,
): ResourceFileEditorResult {
    val validation = validateCustomResourceFileName(name, displayedSettings.customFiles)
    if (validation !is ResourceFileNameValidation.Valid) {
        return ResourceFileEditorResult.Rejected(validation)
    }

    var addedFile: TrafficConfigCustomResourceFile? = null
    var nextCustomFiles = displayedSettings.customFiles
    updateSettings { settings ->
        val file = TrafficConfigCustomResourceFile(
            id = settings.nextCustomFileId,
            name = validation.name,
            url = url.trim(),
        )
        addedFile = file
        nextCustomFiles = settings.customFiles + file
        settings.copy(
            customFiles = nextCustomFiles,
            nextCustomFileId = file.id + 1,
        )
    }

    addedFile?.takeIf { file -> file.url.isBlank() }?.let { file ->
        afterCommit(ResourceFileEditorEffect.ReplaceCustom(file, nextCustomFiles))
    }
    return ResourceFileEditorResult.Saved
}

/** Edits metadata using the latest settings, then emits the storage rename after that commit. */
fun editCustomResourceFile(
    displayedSettings: TrafficConfigResourceSettings,
    previousFile: TrafficConfigCustomResourceFile,
    name: String,
    url: String,
    updateSettings: ((TrafficConfigResourceSettings) -> TrafficConfigResourceSettings) -> Unit,
    afterCommit: (ResourceFileEditorEffect) -> Unit,
): ResourceFileEditorResult {
    val validation = validateCustomResourceFileName(
        name = name,
        customFiles = displayedSettings.customFiles,
        editingFileId = previousFile.id,
    )
    if (validation !is ResourceFileNameValidation.Valid) {
        return ResourceFileEditorResult.Rejected(validation)
    }

    var editedFile: TrafficConfigCustomResourceFile? = null
    var nextCustomFiles = displayedSettings.customFiles
    updateSettings { settings ->
        val file = previousFile.copy(name = validation.name, url = url.trim())
        editedFile = file
        nextCustomFiles = settings.customFiles.map { current ->
            if (current.id == previousFile.id) file else current
        }
        settings.copy(customFiles = nextCustomFiles)
    }

    editedFile?.let { file ->
        afterCommit(ResourceFileEditorEffect.RenameCustom(previousFile, file, nextCustomFiles))
    }
    return ResourceFileEditorResult.Saved
}

/** Removes the id from the latest metadata and emits the host delete effect afterward. */
fun deleteCustomResourceFile(
    file: TrafficConfigCustomResourceFile,
    updateSettings: ((TrafficConfigResourceSettings) -> TrafficConfigResourceSettings) -> Unit,
    afterCommit: (ResourceFileEditorEffect) -> Unit,
) {
    var remainingCustomFiles = emptyList<TrafficConfigCustomResourceFile>()
    updateSettings { settings ->
        remainingCustomFiles = settings.customFiles.filterNot { current -> current.id == file.id }
        settings.copy(customFiles = remainingCustomFiles)
    }
    afterCommit(ResourceFileEditorEffect.DeleteCustom(file, remainingCustomFiles))
}
