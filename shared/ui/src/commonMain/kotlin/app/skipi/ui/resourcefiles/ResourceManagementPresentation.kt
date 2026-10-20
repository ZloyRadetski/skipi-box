// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.resourcefiles

import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.resources.ResourceFileEditorResult
import features.resources.ResourceFileSourceDraft
import features.resources.ResourceFileUpdateDisplayState
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileUpdateSource
import features.resources.beginCustomResourceFileSourceEdit

enum class ResourceFileRowActionKind {
    Update,
    Replace,
    Restore,
    Edit,
    Delete,
}

data class ResourceFileRowAction(
    val kind: ResourceFileRowActionKind,
    val enabled: Boolean,
    val updateDisplayState: ResourceFileUpdateDisplayState? = null,
)

enum class ResourceFileSourceActionKind {
    Update,
    Cancel,
}

data class ResourceFileSourceAction(
    val kind: ResourceFileSourceActionKind,
    val enabled: Boolean,
)

sealed interface ResourceFileSourceSelection {
    data class PersistSource(val sourceId: Int) : ResourceFileSourceSelection

    data class OpenCustomEditor(val draft: ResourceFileSourceDraft) : ResourceFileSourceSelection
}

/** UI-only dialogs, kept as one immutable value so individual reducer actions preserve siblings. */
data class ResourceFileManagementDialogState(
    val showAddEditor: Boolean = false,
    val editingFile: TrafficConfigCustomResourceFile? = null,
    val sourceDraft: ResourceFileSourceDraft? = null,
    val pendingDeletion: TrafficConfigCustomResourceFile? = null,
)

data class ResourceFileDeletionRequest(
    val state: ResourceFileManagementDialogState,
    val deleteImmediately: TrafficConfigCustomResourceFile? = null,
)

data class ResourceFileDeletionConfirmation(
    val state: ResourceFileManagementDialogState,
    val fileToDelete: TrafficConfigCustomResourceFile?,
)

/** Displays option zero for stale source ids without changing the persisted profile value. */
fun resourceFileSourceDisplayIndex(sourceId: Int, sourceCount: Int): Int =
    sourceId.takeIf { it in 0 until sourceCount } ?: 0

/** Resolves whether selecting the custom source opens a draft or commits a preset selection. */
fun selectResourceFileSourceForUi(
    settings: TrafficConfigResourceSettings,
    selectedSourceId: Int,
    resolvedSelectedSource: ResourceFileUpdateSource,
): ResourceFileSourceSelection = if (selectedSourceId == ResourceFileSourceCustom) {
    ResourceFileSourceSelection.OpenCustomEditor(
        beginCustomResourceFileSourceEdit(settings, resolvedSelectedSource),
    )
} else {
    ResourceFileSourceSelection.PersistSource(selectedSourceId)
}

/** Projects the same ordered row controls and enabled states as the Android resource cards. */
fun resourceFileRowActions(
    isCustom: Boolean,
    url: String,
    updateDisplayState: ResourceFileUpdateDisplayState,
    actionsEnabled: Boolean,
): List<ResourceFileRowAction> {
    val rowActionsEnabled = actionsEnabled && updateDisplayState == ResourceFileUpdateDisplayState.Idle
    return buildList {
        if (!isCustom || url.isNotBlank()) {
            add(
                ResourceFileRowAction(
                    kind = ResourceFileRowActionKind.Update,
                    enabled = actionsEnabled && updateDisplayState == ResourceFileUpdateDisplayState.Idle,
                    updateDisplayState = updateDisplayState,
                ),
            )
        }
        add(ResourceFileRowAction(ResourceFileRowActionKind.Replace, rowActionsEnabled))
        if (isCustom) {
            add(ResourceFileRowAction(ResourceFileRowActionKind.Edit, rowActionsEnabled))
            add(ResourceFileRowAction(ResourceFileRowActionKind.Delete, rowActionsEnabled))
        } else {
            add(ResourceFileRowAction(ResourceFileRowActionKind.Restore, rowActionsEnabled))
        }
    }
}

/** The source action remains an enabled Cancel while the queue is busy. */
fun resourceFileSourceAction(queueBusy: Boolean, actionsEnabled: Boolean): ResourceFileSourceAction =
    if (queueBusy) {
        ResourceFileSourceAction(ResourceFileSourceActionKind.Cancel, enabled = true)
    } else {
        ResourceFileSourceAction(ResourceFileSourceActionKind.Update, enabled = actionsEnabled)
    }

/** Stores a pending file only when confirmation is enabled; otherwise the caller deletes now. */
fun requestResourceFileDeletion(
    state: ResourceFileManagementDialogState,
    file: TrafficConfigCustomResourceFile,
    confirmationEnabled: Boolean,
): ResourceFileDeletionRequest = if (confirmationEnabled) {
    ResourceFileDeletionRequest(state.copy(pendingDeletion = file))
} else {
    ResourceFileDeletionRequest(state = state, deleteImmediately = file)
}

fun dismissResourceFileDeletion(state: ResourceFileManagementDialogState): ResourceFileManagementDialogState =
    state.copy(pendingDeletion = null)

fun confirmResourceFileDeletion(state: ResourceFileManagementDialogState): ResourceFileDeletionConfirmation =
    ResourceFileDeletionConfirmation(
        state = state.copy(pendingDeletion = null),
        fileToDelete = state.pendingDeletion,
    )

/** Closes only the editor just saved, leaving source and delete dialogs untouched. */
fun acceptResourceFileEditorResult(
    state: ResourceFileManagementDialogState,
    result: ResourceFileEditorResult,
    editing: Boolean,
): ResourceFileManagementDialogState {
    if (result !is ResourceFileEditorResult.Saved) return state
    return if (editing) {
        state.copy(editingFile = null)
    } else {
        state.copy(showAddEditor = false)
    }
}
