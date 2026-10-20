// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.resourcefiles

import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.resources.ResourceFileEditorResult
import features.resources.ResourceFileNameValidation
import features.resources.ResourceFileUpdateDisplayState
import features.resources.ResourceFileUpdateSources
import features.resources.ResourceFileSourceCustom
import features.resources.resourceFileUpdateSourceAt
import features.resources.resourceFileUpdateSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ResourceManagementInteractionTest {
    @Test
    fun invalidStoredSourceShowsFirstOptionWithoutChangingStoredValue() {
        val settings = TrafficConfigResourceSettings(source = 99)

        assertEquals(0, resourceFileSourceDisplayIndex(settings.source, sourceCount = 6))
        assertEquals(99, settings.source)
    }

    @Test
    fun choosingBuiltInProviderPersistsOnlyItsId() {
        val settings = TrafficConfigResourceSettings(
            source = ResourceFileSourceCustom,
            customGeoIpUrl = "https://custom.example/ip",
            customGeoSiteUrl = "https://custom.example/site",
        )
        val selectedId = ResourceFileUpdateSources[2].id

        val selection = selectResourceFileSourceForUi(
            settings = settings,
            selectedSourceId = selectedId,
            resolvedSelectedSource = settings.resourceFileUpdateSource(),
        )

        assertEquals(ResourceFileSourceSelection.PersistSource(selectedId), selection)
        assertEquals(ResourceFileSourceCustom, settings.source)
        assertEquals("https://custom.example/ip", settings.customGeoIpUrl)
        assertEquals("https://custom.example/site", settings.customGeoSiteUrl)
    }

    @Test
    fun choosingCustomOpensAnUncommittedDraftUsingCurrentProviderFallbacks() {
        val currentProvider = resourceFileUpdateSourceAt(3)
        val settings = TrafficConfigResourceSettings(
            source = currentProvider.id,
            customGeoIpUrl = "",
            customGeoSiteUrl = "  https://saved.example/site  ",
        )

        val selection = selectResourceFileSourceForUi(
            settings = settings,
            selectedSourceId = ResourceFileSourceCustom,
            resolvedSelectedSource = settings.resourceFileUpdateSource(),
        )

        val open = assertIs<ResourceFileSourceSelection.OpenCustomEditor>(selection)
        assertEquals(currentProvider.geoIpUrl, open.draft.geoIpUrl)
        assertEquals("  https://saved.example/site  ", open.draft.geoSiteUrl)
        assertEquals(currentProvider.id, settings.source, "opening/cancelling the draft persists nothing")
        assertEquals("", settings.customGeoIpUrl)
    }

    @Test
    fun rowActionsPreserveBuiltInAndCustomRules() {
        val builtIn = resourceFileRowActions(
            isCustom = false,
            url = "",
            updateDisplayState = ResourceFileUpdateDisplayState.Idle,
            actionsEnabled = true,
        )
        assertEquals(
            listOf(ResourceFileRowActionKind.Update, ResourceFileRowActionKind.Replace, ResourceFileRowActionKind.Restore),
            builtIn.map { action -> action.kind },
        )
        assertTrue(builtIn.all { action -> action.enabled })

        val blankCustom = resourceFileRowActions(
            isCustom = true,
            url = "  ",
            updateDisplayState = ResourceFileUpdateDisplayState.Idle,
            actionsEnabled = true,
        )
        assertEquals(
            listOf(ResourceFileRowActionKind.Replace, ResourceFileRowActionKind.Edit, ResourceFileRowActionKind.Delete),
            blankCustom.map { action -> action.kind },
            "a blank custom URL hides only Update",
        )
        assertTrue(blankCustom.all { action -> action.enabled })

        val queuedCustom = resourceFileRowActions(
            isCustom = true,
            url = "https://rules.example/custom.dat",
            updateDisplayState = ResourceFileUpdateDisplayState.Queued,
            actionsEnabled = true,
        )
        assertEquals(
            listOf(ResourceFileRowActionKind.Update, ResourceFileRowActionKind.Replace, ResourceFileRowActionKind.Edit, ResourceFileRowActionKind.Delete),
            queuedCustom.map { action -> action.kind },
        )
        assertTrue(queuedCustom.none { action -> action.enabled }, "queued target has disabled row actions")
        assertEquals(ResourceFileUpdateDisplayState.Queued, queuedCustom.first().updateDisplayState)
    }

    @Test
    fun runningOrOneOffActionKeepsRowControlsDisabled() {
        val running = resourceFileRowActions(
            isCustom = false,
            url = "",
            updateDisplayState = ResourceFileUpdateDisplayState.Running,
            actionsEnabled = true,
        )
        assertTrue(running.none { action -> action.enabled })

        val oneOffActionRunning = resourceFileRowActions(
            isCustom = true,
            url = "https://rules.example/custom.dat",
            updateDisplayState = ResourceFileUpdateDisplayState.Idle,
            actionsEnabled = false,
        )
        assertTrue(oneOffActionRunning.none { action -> action.enabled })
    }

    @Test
    fun sourceUpdateSwitchesToEnabledCancelWhileQueueIsBusy() {
        assertEquals(
            ResourceFileSourceAction(kind = ResourceFileSourceActionKind.Cancel, enabled = true),
            resourceFileSourceAction(queueBusy = true, actionsEnabled = false),
        )
        assertEquals(
            ResourceFileSourceAction(kind = ResourceFileSourceActionKind.Update, enabled = false),
            resourceFileSourceAction(queueBusy = false, actionsEnabled = false),
        )
        assertEquals(
            ResourceFileSourceAction(kind = ResourceFileSourceActionKind.Update, enabled = true),
            resourceFileSourceAction(queueBusy = false, actionsEnabled = true),
        )
    }

    @Test
    fun deleteConfirmationDefersAndClearsThePendingFile() {
        val file = TrafficConfigCustomResourceFile(4, "private.dat", "")
        val requested = requestResourceFileDeletion(
            state = ResourceFileManagementDialogState(),
            file = file,
            confirmationEnabled = true,
        )
        assertEquals(file, requested.state.pendingDeletion)
        assertNull(requested.deleteImmediately)

        val dismissed = dismissResourceFileDeletion(requested.state)
        assertNull(dismissed.pendingDeletion)

        val requestedAgain = requestResourceFileDeletion(
            state = dismissed,
            file = file,
            confirmationEnabled = true,
        )
        val confirmed = confirmResourceFileDeletion(requestedAgain.state)
        assertNull(confirmed.state.pendingDeletion)
        assertEquals(file, confirmed.fileToDelete)
    }

    @Test
    fun deleteRunsImmediatelyWhenConfirmationIsDisabled() {
        val file = TrafficConfigCustomResourceFile(4, "private.dat", "")
        val result = requestResourceFileDeletion(
            state = ResourceFileManagementDialogState(),
            file = file,
            confirmationEnabled = false,
        )

        assertNull(result.state.pendingDeletion)
        assertEquals(file, result.deleteImmediately)
    }

    @Test
    fun editorDialogStaysOpenAfterRejectedSaveAndClosesAfterSavedResult() {
        val open = ResourceFileManagementDialogState(showAddEditor = true)
        val rejected = acceptResourceFileEditorResult(
            state = open,
            result = ResourceFileEditorResult.Rejected(ResourceFileNameValidation.Duplicate),
            editing = false,
        )
        assertTrue(rejected.showAddEditor)

        val saved = acceptResourceFileEditorResult(
            state = open,
            result = ResourceFileEditorResult.Saved,
            editing = false,
        )
        assertFalse(saved.showAddEditor)
    }
}
