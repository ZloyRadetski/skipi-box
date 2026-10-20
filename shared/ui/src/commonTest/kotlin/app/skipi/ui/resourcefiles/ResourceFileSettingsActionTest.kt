// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.resourcefiles

import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.resources.ResourceFileEditorEffect
import features.resources.ResourceFileEditorResult
import features.resources.ResourceFileNameValidation
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileSourceDraft
import features.resources.addCustomResourceFile
import features.resources.applyCustomResourceFileSourceDraft
import features.resources.resourceFileUpdateSourceAt
import features.resources.selectResourceFileSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResourceFileSettingsActionTest {
    @Test
    fun successfulSourceSaveKeepsUrlThenSourceThenDismissOrder() {
        val selectedProvider = resourceFileUpdateSourceAt(1)
        val draft = ResourceFileSourceDraft(
            geoIpUrl = "https://custom.invalid/ip",
            geoSiteUrl = "https://custom.invalid/site",
            geoIpOnlyCnPrivateUrl = "https://custom.invalid/private",
            directCidrIpv4Url = "https://custom.invalid/v4",
            directCidrIpv6Url = "https://custom.invalid/v6",
        )
        var latest = TrafficConfigResourceSettings(source = selectedProvider.id)
        val events = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()

        fun persist(
            label: String,
            transform: (TrafficConfigResourceSettings) -> TrafficConfigResourceSettings,
        ) {
            latest = transform(latest)
            events += label
        }

        val completed = runResourceFileSettingsAction(
            action = {
                persist("save custom URLs") { settings ->
                    applyCustomResourceFileSourceDraft(settings, draft, selectedProvider)
                }
                persist("select custom source") { settings ->
                    selectResourceFileSource(settings, ResourceFileSourceCustom)
                }
                events += "dismiss source editor"
            },
            onFailure = { error -> failures += error },
        )

        assertTrue(completed)
        assertEquals(
            listOf("save custom URLs", "select custom source", "dismiss source editor"),
            events,
        )
        assertEquals(ResourceFileSourceCustom, latest.source)
        assertEquals("https://custom.invalid/ip", latest.customGeoIpUrl)
        assertTrue(failures.isEmpty())
    }

    @Test
    fun failedSecondSourceCommitIsReportedOnceAndDoesNotDismissEditor() {
        val failure = IllegalStateException("profile settings save failed")
        val selectedProvider = resourceFileUpdateSourceAt(1)
        val draft = ResourceFileSourceDraft(
            geoIpUrl = "https://custom.invalid/ip",
            geoSiteUrl = "https://custom.invalid/site",
            geoIpOnlyCnPrivateUrl = "https://custom.invalid/private",
            directCidrIpv4Url = "https://custom.invalid/v4",
            directCidrIpv6Url = "https://custom.invalid/v6",
        )
        val initial = TrafficConfigResourceSettings(source = selectedProvider.id)
        var latest = initial
        var settingsWriteCount = 0
        val events = mutableListOf<String>()
        val failures = mutableListOf<Throwable>()

        fun persist(
            label: String,
            transform: (TrafficConfigResourceSettings) -> TrafficConfigResourceSettings,
        ) {
            settingsWriteCount += 1
            events += label
            if (settingsWriteCount == 2) throw failure
            latest = transform(latest)
        }

        val completed = runResourceFileSettingsAction(
            action = {
                persist("save custom URLs") { settings ->
                    applyCustomResourceFileSourceDraft(settings, draft, selectedProvider)
                }
                persist("select custom source") { settings ->
                    selectResourceFileSource(settings, ResourceFileSourceCustom)
                }
                events += "dismiss source editor"
            },
            onFailure = { error -> failures += error },
        )

        assertFalse(completed)
        assertEquals(listOf("save custom URLs", "select custom source"), events)
        assertEquals(initial.source, latest.source)
        assertEquals("https://custom.invalid/ip", latest.customGeoIpUrl)
        assertEquals(1, failures.size)
        assertSame(failure, failures.single())
    }

    @Test
    fun failedMetadataSaveDoesNotDispatchBlankCustomFileReplacementOrCloseEditor() {
        val failure = IllegalStateException("profile settings save failed")
        val settings = TrafficConfigResourceSettings(customFiles = emptyList(), nextCustomFileId = 1)
        val persistedSettings = settings
        var attemptedSettings: TrafficConfigResourceSettings? = null
        var dialogs = ResourceFileManagementDialogState(showAddEditor = true)
        val failures = mutableListOf<Throwable>()
        var dispatchedEffect: ResourceFileEditorEffect? = null
        var settingsWriteCount = 0

        val completed = runResourceFileSettingsAction(
            action = {
                addCustomResourceFile(
                    displayedSettings = settings,
                    name = "CustomRules",
                    url = "",
                    updateSettings = { transform ->
                        attemptedSettings = transform(persistedSettings)
                        settingsWriteCount += 1
                        throw failure
                    },
                    afterCommit = { effect -> dispatchedEffect = effect },
                ).also { result ->
                    dialogs = acceptResourceFileEditorResult(dialogs, result, editing = false)
                }
            },
            onFailure = { error -> failures += error },
        )

        assertFalse(completed)
        assertEquals(1, settingsWriteCount)
        assertEquals(settings, persistedSettings, "failed persistence leaves the saved profile unchanged")
        assertEquals("CustomRules", attemptedSettings?.customFiles?.single()?.name)
        assertEquals(null, dispatchedEffect)
        assertTrue(dialogs.showAddEditor, "failed persistence must not close the editor")
        assertEquals(1, failures.size)
        assertSame(failure, failures.single())
    }

    @Test
    fun validationRejectionRemainsACompletedActionAndKeepsEditorOpen() {
        val settings = TrafficConfigResourceSettings(
            customFiles = listOf(TrafficConfigCustomResourceFile(1, "Reserved", "https://rules.invalid")),
            nextCustomFileId = 2,
        )
        val failures = mutableListOf<Throwable>()
        var result: ResourceFileEditorResult? = null

        val completed = runResourceFileSettingsAction(
            action = {
                result = addCustomResourceFile(
                    displayedSettings = settings,
                    name = "Reserved",
                    url = "https://other.invalid",
                    updateSettings = { error("rejected names must not persist") },
                    afterCommit = { error("rejected names must not dispatch storage work") },
                )
            },
            onFailure = { error -> failures += error },
        )
        val dialogAfterSave = acceptResourceFileEditorResult(
            state = ResourceFileManagementDialogState(showAddEditor = true),
            result = requireNotNull(result),
            editing = false,
        )

        assertTrue(completed)
        assertEquals(ResourceFileEditorResult.Rejected(ResourceFileNameValidation.Duplicate), result)
        assertTrue(dialogAfterSave.showAddEditor)
        assertTrue(failures.isEmpty())
    }
}
