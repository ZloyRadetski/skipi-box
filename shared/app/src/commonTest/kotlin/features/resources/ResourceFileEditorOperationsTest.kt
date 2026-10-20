// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.customResourceFileNameOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ResourceFileEditorOperationsTest {
    @Test
    fun customNamesKeepTheAndroidValidationAndCaseSensitiveDuplicateRules() {
        val current = listOf(file(1, "MapList", "https://one.invalid"))

        listOf("", " ", " leading", "trailing ", "two words", "a:b", "a/b", "a\\b", ".", "..").forEach { name ->
            assertEquals(
                ResourceFileNameValidation.Invalid,
                validateCustomResourceFileName(name, current),
                "should reject <$name>",
            )
        }
        assertEquals(
            ResourceFileNameValidation.Duplicate,
            validateCustomResourceFileName("MapList", current),
        )
        assertEquals(
            ResourceFileNameValidation.Valid("maplist"),
            validateCustomResourceFileName("maplist", current),
            "duplicate detection is exact/case-sensitive",
        )
        assertEquals("odd*?.dat", customResourceFileNameOrNull("odd*?.dat"))
        assertEquals(
            ResourceFileKind.GeoIp.fileName,
            customResourceFileNameOrNull(ResourceFileKind.GeoIp.fileName),
            "the generic filename predicate accepts built-in names; the editor reserves them separately",
        )
        assertEquals(
            ResourceFileNameValidation.Valid("MapList"),
            validateCustomResourceFileName("MapList", current, editingFileId = 1),
            "editing excludes its own id from the reserved set",
        )
    }

    @Test
    fun everyBuiltInFilenameIsReservedForCustomFiles() {
        ResourceFileKind.entries.forEach { kind ->
            assertEquals(
                ResourceFileNameValidation.Duplicate,
                validateCustomResourceFileName(kind.fileName, emptyList()),
                "built-in name ${kind.fileName} is reserved",
            )
        }
    }

    @Test
    fun addingAllocatesFromLatestSettingsAndRequestsBlankUrlReplacementAfterCommit() {
        val captured = settings(
            files = listOf(file(2, "Existing", "https://existing.invalid")),
            nextId = 3,
        )
        val latestOnlyFile = file(8, "AddedElsewhere", "https://elsewhere.invalid")
        var latest = captured.copy(
            customFiles = captured.customFiles + latestOnlyFile,
            nextCustomFileId = 41,
        )
        val events = mutableListOf<String>()
        var dispatched: ResourceFileEditorEffect? = null

        val result = addCustomResourceFile(
            displayedSettings = captured,
            name = "NewMap",
            url = "  \n  ",
            updateSettings = { transform ->
                val updated = transform(latest)
                latest = updated
                events += "settings committed"
            },
            afterCommit = { effect ->
                assertTrue(latest.customFiles.any { it.name == "NewMap" })
                dispatched = effect
                events += "replacement requested"
            },
        )

        assertIs<ResourceFileEditorResult.Saved>(result)
        assertEquals(41, latest.customFiles.last().id)
        assertEquals("NewMap", latest.customFiles.last().name)
        assertEquals("", latest.customFiles.last().url)
        assertEquals(42, latest.nextCustomFileId)
        assertEquals(captured.customFiles + latestOnlyFile + file(41, "NewMap", ""), latest.customFiles)
        assertEquals(
            ResourceFileEditorEffect.ReplaceCustom(
                file = file(41, "NewMap", ""),
                customFiles = latest.customFiles,
            ),
            dispatched,
        )
        assertEquals(listOf("settings committed", "replacement requested"), events)
    }

    @Test
    fun addingRemoteCustomFileTrimsUrlWithoutOpeningReplacementPicker() {
        val initial = settings(files = emptyList(), nextId = 9)
        var latest = initial
        var effectCount = 0

        val result = addCustomResourceFile(
            displayedSettings = initial,
            name = "RemoteList",
            url = "  https://example.invalid/list.dat  ",
            updateSettings = { transform -> latest = transform(latest) },
            afterCommit = { effectCount += 1 },
        )

        assertIs<ResourceFileEditorResult.Saved>(result)
        assertEquals(file(9, "RemoteList", "https://example.invalid/list.dat"), latest.customFiles.single())
        assertEquals(10, latest.nextCustomFileId)
        assertEquals(0, effectCount)
    }

    @Test
    fun invalidAddDoesNotCommitMetadataOrDispatchStorageWork() {
        val initial = settings(files = listOf(file(1, "Used", "")), nextId = 2)
        var commitCount = 0
        var effectCount = 0

        val result = addCustomResourceFile(
            displayedSettings = initial,
            name = "Used",
            url = "",
            updateSettings = { transform -> commitCount += 1; transform(initial) },
            afterCommit = { effectCount += 1 },
        )

        assertEquals(ResourceFileEditorResult.Rejected(ResourceFileNameValidation.Duplicate), result)
        assertEquals(0, commitCount)
        assertEquals(0, effectCount)
    }

    @Test
    fun editingKeepsIdAndDispatchesRenameOnlyAfterLatestSettingsCommit() {
        val edited = file(5, "Before", "https://before.invalid")
        val sibling = file(6, "Sibling", "https://sibling.invalid")
        val captured = settings(files = listOf(edited, sibling), nextId = 7)
        var latest = captured.copy(customFiles = captured.customFiles + file(9, "Concurrent", ""))
        val events = mutableListOf<String>()
        var dispatched: ResourceFileEditorEffect? = null

        val result = editCustomResourceFile(
            displayedSettings = captured,
            previousFile = edited,
            name = "Before",
            url = "  https://new.invalid/file  ",
            updateSettings = { transform ->
                val updated = transform(latest)
                latest = updated
                events += "settings committed"
            },
            afterCommit = { effect ->
                assertEquals("https://new.invalid/file", latest.customFiles.first { it.id == 5 }.url)
                dispatched = effect
                events += "rename requested"
            },
        )

        assertIs<ResourceFileEditorResult.Saved>(result)
        assertEquals(
            listOf(file(5, "Before", "https://new.invalid/file"), sibling, file(9, "Concurrent", "")),
            latest.customFiles,
        )
        assertEquals(7, latest.nextCustomFileId)
        assertEquals(
            ResourceFileEditorEffect.RenameCustom(
                previousFile = edited,
                file = file(5, "Before", "https://new.invalid/file"),
                customFiles = latest.customFiles,
            ),
            dispatched,
        )
        assertEquals(listOf("settings committed", "rename requested"), events)
    }

    @Test
    fun editingAnotherCustomFilesNameIsRejectedWithoutMetadataOrRename() {
        val first = file(1, "First", "")
        val second = file(2, "Second", "")
        val initial = settings(files = listOf(first, second), nextId = 3)
        var commitCount = 0
        var effectCount = 0

        val result = editCustomResourceFile(
            displayedSettings = initial,
            previousFile = second,
            name = "First",
            url = "https://new.invalid",
            updateSettings = { transform -> commitCount += 1; transform(initial) },
            afterCommit = { effectCount += 1 },
        )

        assertEquals(ResourceFileEditorResult.Rejected(ResourceFileNameValidation.Duplicate), result)
        assertEquals(0, commitCount)
        assertEquals(0, effectCount)
    }

    @Test
    fun deleteRemovesOnlyTheMatchingIdAndDispatchesAfterMetadataCommit() {
        val removed = file(4, "RemoveMe", "")
        val sibling = file(5, "KeepMe", "https://keep.invalid")
        val captured = settings(files = listOf(removed, sibling), nextId = 6)
        var latest = captured.copy(customFiles = captured.customFiles + file(8, "Latest", ""))
        val events = mutableListOf<String>()
        var dispatched: ResourceFileEditorEffect? = null

        deleteCustomResourceFile(
            file = removed,
            updateSettings = { transform ->
                val updated = transform(latest)
                latest = updated
                events += "settings committed"
                updated
            },
            afterCommit = { effect ->
                assertTrue(latest.customFiles.none { it.id == removed.id })
                dispatched = effect
                events += "delete requested"
            },
        )

        assertEquals(listOf(sibling, file(8, "Latest", "")), latest.customFiles)
        assertEquals(6, latest.nextCustomFileId)
        assertEquals(
            ResourceFileEditorEffect.DeleteCustom(
                file = removed,
                customFiles = latest.customFiles,
            ),
            dispatched,
        )
        assertEquals(listOf("settings committed", "delete requested"), events)
    }

    @Test
    fun customSourceDraftUsesResolvedProviderAndSaveTrimsWithPerFieldFallback() {
        val selectedProvider = resourceFileUpdateSourceAt(3)
        val original = settings(files = emptyList(), nextId = 1).copy(
            source = selectedProvider.id,
            customGeoIpUrl = "",
            customGeoSiteUrl = "  https://stored.invalid/site  ",
            customGeoIpOnlyCnPrivateUrl = "",
            customDirectCidrIpv4Url = "   ",
            customDirectCidrIpv6Url = "https://stored.invalid/v6",
        )
        val draft = beginCustomResourceFileSourceEdit(original, selectedProvider)

        assertEquals(selectedProvider.geoIpUrl, draft.geoIpUrl)
        assertEquals("  https://stored.invalid/site  ", draft.geoSiteUrl)
        assertEquals(selectedProvider.geoIpOnlyCnPrivateUrl, draft.geoIpOnlyCnPrivateUrl)
        assertEquals(selectedProvider.directCidrIpv4Url, draft.directCidrIpv4Url)
        assertEquals("https://stored.invalid/v6", draft.directCidrIpv6Url)
        // Cancel is represented by not persisting this local draft.
        val editedDraft = draft.copy(
            geoIpUrl = "  https://custom.invalid/ip  ",
            geoSiteUrl = "  ",
            geoIpOnlyCnPrivateUrl = " https://custom.invalid/private ",
            directCidrIpv4Url = "\n",
            directCidrIpv6Url = " https://custom.invalid/v6 ",
        )
        val savedUrls = applyCustomResourceFileSourceDraft(original, editedDraft, selectedProvider)

        assertEquals(original.source, savedUrls.source, "first Save callback writes URLs only")
        assertEquals("https://custom.invalid/ip", savedUrls.customGeoIpUrl)
        assertEquals(selectedProvider.geoSiteUrl, savedUrls.customGeoSiteUrl)
        assertEquals("https://custom.invalid/private", savedUrls.customGeoIpOnlyCnPrivateUrl)
        assertEquals(selectedProvider.directCidrIpv4Url, savedUrls.customDirectCidrIpv4Url)
        assertEquals("https://custom.invalid/v6", savedUrls.customDirectCidrIpv6Url)

        val latestWithUrls = updateSettings(original) { current ->
            applyCustomResourceFileSourceDraft(current, editedDraft, selectedProvider)
        }
        assertEquals(original.source, latestWithUrls.source)
        assertEquals(savedUrls.customGeoIpUrl, latestWithUrls.customGeoIpUrl)
        val latest = updateSettings(latestWithUrls) { current ->
            selectResourceFileSource(current, ResourceFileSourceCustom)
        }
        assertEquals(ResourceFileSourceCustom, latest.source)
        assertEquals(savedUrls.customGeoSiteUrl, latest.customGeoSiteUrl)
    }

    @Test
    fun selectingBuiltInProviderChangesOnlySourceAndDoesNotOpenOrRewriteCustomDraft() {
        val initial = settings(files = emptyList(), nextId = 1).copy(
            source = ResourceFileSourceCustom,
            customGeoIpUrl = "https://custom.invalid/ip",
            customGeoSiteUrl = "https://custom.invalid/site",
        )
        val selected = selectResourceFileSource(initial, resourceFileUpdateSourceAt(2).id)

        assertEquals(resourceFileUpdateSourceAt(2).id, selected.source)
        assertEquals(initial.customGeoIpUrl, selected.customGeoIpUrl)
        assertEquals(initial.customGeoSiteUrl, selected.customGeoSiteUrl)
        assertEquals(initial.customGeoIpOnlyCnPrivateUrl, selected.customGeoIpOnlyCnPrivateUrl)
        assertEquals(initial.customDirectCidrIpv4Url, selected.customDirectCidrIpv4Url)
        assertEquals(initial.customDirectCidrIpv6Url, selected.customDirectCidrIpv6Url)
    }

    private fun settings(
        files: List<TrafficConfigCustomResourceFile>,
        nextId: Int,
    ) = TrafficConfigResourceSettings(customFiles = files, nextCustomFileId = nextId)

    private fun file(id: Int, name: String, url: String) = TrafficConfigCustomResourceFile(id, name, url)

    private fun updateSettings(
        initial: TrafficConfigResourceSettings,
        transform: (TrafficConfigResourceSettings) -> TrafficConfigResourceSettings,
    ): TrafficConfigResourceSettings = transform(initial)
}
