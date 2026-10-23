// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import features.clipboard.ClipboardImportMode
import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigNetworkActivation
import features.config.TrafficConfigNetworkTransportCellular
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.proxy.app.usecase.ProxyAppListClipboardData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class TrafficConfigPerAppEditorControllerTest {
    @Test
    fun modeChangeUpdatesLatestTargetAndPreservesSelectedIdsAndOtherProfileData() {
        val staleTarget = profile(
            id = 7,
            mode = ProxyAppListModeGlobal,
            selectedApps = listOf("0:stale.package"),
        )
        val sibling = profile(id = 3, selectedApps = listOf("legacy.sibling"))
        val store = MapProfileStore(staleTarget, sibling)
        val latestTarget = profile(
            id = 7,
            mode = ProxyAppListModeBlacklist,
            selectedApps = listOf("legacy.unknown", "10:com.example.selected"),
        ).copy(
            name = "Latest target",
            rawConfig = "[Vendor]\nlatest-target = keep\n",
            sourceUrl = "https://latest.example/profile.conf",
            lastUpdatedAtMillis = 777L,
        )
        store.replace(latestTarget)
        val controller = TrafficConfigEditorController(store)

        val result = controller.setProxyAppListMode(7, ProxyAppListModeWhitelist)

        assertIs<TrafficConfigEditorResult.Saved>(result)
        assertEquals(latestTarget.copy(proxyAppListMode = ProxyAppListModeWhitelist), store.profile(7))
        assertEquals(sibling, store.profile(3))
    }

    @Test
    fun sharedUidSelectionAddsAndRemovesEveryKeyWhileKeepingUnknownIdsInOrder() {
        val initial = profile(
            id = 7,
            selectedApps = listOf(
                "legacy.unknown",
                "0:com.example.first",
                "1:com.example.other",
            ),
        )
        val sibling = profile(id = 3, selectedApps = listOf("legacy.sibling"))
        val store = MapProfileStore(initial, sibling)
        val controller = TrafficConfigEditorController(store)
        val sharedUidKeys = listOf(
            "0:com.example.first",
            "0:com.example.second",
            "0:com.example.first",
        )

        val added = controller.setProxyAppListSelection(
            profileId = 7,
            selectionKeys = sharedUidKeys,
            isChecked = true,
        )
        assertIs<TrafficConfigEditorResult.Saved>(added)
        val afterAdd = initial.copy(
            proxyAppListSelectedApps = listOf(
                "legacy.unknown",
                "0:com.example.first",
                "1:com.example.other",
                "0:com.example.second",
            ),
        )
        assertEquals(afterAdd, store.profile(7))

        val removed = controller.setProxyAppListSelection(
            profileId = 7,
            selectionKeys = sharedUidKeys,
            isChecked = false,
        )
        assertIs<TrafficConfigEditorResult.Saved>(removed)
        assertEquals(
            initial.copy(proxyAppListSelectedApps = listOf("legacy.unknown", "1:com.example.other")),
            store.profile(7),
        )
        assertEquals(sibling, store.profile(3))
    }

    @Test
    fun replaceUsesImportedModeAndReportsResultingSelectedIdCount() {
        val initial = profile(
            id = 7,
            mode = ProxyAppListModeBlacklist,
            selectedApps = listOf("0:com.example.old"),
        )
        val sibling = profile(id = 3, selectedApps = listOf("legacy.sibling"))
        val store = MapProfileStore(initial, sibling)
        val controller = TrafficConfigEditorController(store)

        val outcome: TrafficConfigProxyAppListImportResult = controller.applyProxyAppListImport(
            profileId = 7,
            imported = ProxyAppListClipboardData(
                mode = ProxyAppListModeWhitelist,
                selectedApps = listOf("0:com.example.one", "10:com.example.two"),
            ),
            importMode = ClipboardImportMode.Replace,
        )

        assertIs<TrafficConfigEditorResult.Saved>(outcome.updateResult)
        assertEquals(2, outcome.importedCount)
        assertEquals(
            initial.copy(
                proxyAppListMode = ProxyAppListModeWhitelist,
                proxyAppListSelectedApps = listOf("0:com.example.one", "10:com.example.two"),
            ),
            store.profile(7),
        )
        assertEquals(sibling, store.profile(3))
    }

    @Test
    fun mergeKeepsCurrentModeWhenClipboardHasNoModeAndCountsNewResultEntries() {
        val initial = profile(
            id = 7,
            mode = ProxyAppListModeGlobal,
            selectedApps = listOf("0:com.example.existing", "legacy.unknown"),
        )
        val store = MapProfileStore(initial)
        val controller = TrafficConfigEditorController(store)

        val outcome = controller.applyProxyAppListImport(
            profileId = 7,
            imported = ProxyAppListClipboardData(
                mode = null,
                selectedApps = listOf(
                    "0:com.example.existing",
                    "10:com.example.new-one",
                    "10:com.example.new-two",
                ),
            ),
            importMode = ClipboardImportMode.Merge,
        )

        assertIs<TrafficConfigEditorResult.Saved>(outcome.updateResult)
        assertEquals(2, outcome.importedCount)
        assertEquals(
            initial.copy(
                proxyAppListSelectedApps = listOf(
                    "0:com.example.existing",
                    "legacy.unknown",
                    "10:com.example.new-one",
                    "10:com.example.new-two",
                ),
            ),
            store.profile(7),
        )
    }

    @Test
    fun mergeCountClampsAtZeroWhenMergedResultIsShorterThanCurrentList() {
        val initial = profile(
            id = 7,
            mode = ProxyAppListModeBlacklist,
            selectedApps = listOf(
                " 0:com.example.existing ",
                "0:com.example.existing",
                "legacy.unknown",
            ),
        )
        val store = MapProfileStore(initial)
        val controller = TrafficConfigEditorController(store)

        val outcome = controller.applyProxyAppListImport(
            profileId = 7,
            imported = ProxyAppListClipboardData(
                mode = null,
                selectedApps = listOf("0:com.example.existing"),
            ),
            importMode = ClipboardImportMode.Merge,
        )

        assertIs<TrafficConfigEditorResult.Saved>(outcome.updateResult)
        assertEquals(0, outcome.importedCount)
        assertEquals(
            initial.copy(proxyAppListSelectedApps = listOf("0:com.example.existing", "legacy.unknown")),
            store.profile(7),
        )
    }

    @Test
    fun missingTargetReturnsMissingAndZeroCountWithoutChangingSibling() {
        val sibling = profile(id = 3, selectedApps = listOf("legacy.sibling"))
        val store = MapProfileStore(sibling)
        val controller = TrafficConfigEditorController(store)

        val outcome = controller.applyProxyAppListImport(
            profileId = 7,
            imported = ProxyAppListClipboardData(
                mode = ProxyAppListModeWhitelist,
                selectedApps = listOf("0:com.example.imported"),
            ),
            importMode = ClipboardImportMode.Replace,
        )

        assertEquals(TrafficConfigEditorResult.Missing, outcome.updateResult)
        assertEquals(0, outcome.importedCount)
        assertEquals(sibling, store.profile(3))
    }

    @Test
    fun handledStoreFailureReturnsFailedAndZeroCountWithoutReportingSuccess() {
        val failure = IllegalStateException("storage unavailable")
        val target = profile(
            id = 7,
            mode = ProxyAppListModeBlacklist,
            selectedApps = listOf("0:com.example.old"),
        )
        val store = TransformThenFailingProfileStore(target, failure)
        val reportedFailures = mutableListOf<Exception>()
        val controller = TrafficConfigEditorController(
            store = store,
            onSaveFailure = { reportedFailures += it },
        )

        val outcome = controller.applyProxyAppListImport(
            profileId = 7,
            imported = ProxyAppListClipboardData(
                mode = ProxyAppListModeWhitelist,
                selectedApps = listOf("0:com.example.imported"),
            ),
            importMode = ClipboardImportMode.Replace,
        )

        val failed = assertIs<TrafficConfigEditorResult.Failed>(outcome.updateResult)
        assertSame(failure, failed.cause)
        assertEquals(listOf<Exception>(failure), reportedFailures)
        assertEquals(0, outcome.importedCount)
        assertEquals(target, store.profile)
        assertEquals(
            target.copy(
                proxyAppListMode = ProxyAppListModeWhitelist,
                proxyAppListSelectedApps = listOf("0:com.example.imported"),
            ),
            store.attemptedProfile,
        )
    }

    private fun profile(
        id: Int,
        mode: Int = ProxyAppListModeGlobal,
        selectedApps: List<String> = emptyList(),
    ): TrafficConfigState = TrafficConfigState(
        id = id,
        name = "Profile $id",
        rawConfig = "[Vendor]\nprofile-$id = keep\n",
        sourceUrl = "https://example.test/profile-$id.conf",
        updateLocked = id % 2 == 0,
        lastUpdatedAtMillis = id * 100L,
        autoUpdate = id % 2 != 0,
        updateInterval = "${id + 1}",
        proxyAppListMode = mode,
        proxyAppListSelectedApps = selectedApps,
        androidSettings = TrafficConfigAndroidSettings(
            enableFakeDns = true,
            tunVpnDns = "10.0.$id.1",
            directDnsDomains = listOf("profile-$id.example"),
        ),
        networkActivation = TrafficConfigNetworkActivation(
            enabled = true,
            transport = TrafficConfigNetworkTransportCellular,
        ),
        resourceSettings = TrafficConfigResourceSettings(
            userAgent = "Profile/$id",
            autoUpdate = false,
            updateInterval = "99",
        ),
    )

    private class MapProfileStore(vararg initialProfiles: TrafficConfigState) : TrafficConfigEditorProfileStore {
        private val profiles = initialProfiles.associateBy(TrafficConfigState::id).toMutableMap()

        fun replace(profile: TrafficConfigState) {
            profiles[profile.id] = profile
        }

        fun profile(id: Int): TrafficConfigState? = profiles[id]

        override fun update(
            profileId: Int,
            transform: (TrafficConfigState) -> TrafficConfigState,
        ): Boolean {
            val current = profiles[profileId] ?: return false
            profiles[profileId] = transform(current)
            return true
        }
    }

    private class TransformThenFailingProfileStore(
        val profile: TrafficConfigState,
        private val failure: Exception,
    ) : TrafficConfigEditorProfileStore {
        var attemptedProfile: TrafficConfigState? = null
            private set

        override fun update(
            profileId: Int,
            transform: (TrafficConfigState) -> TrafficConfigState,
        ): Boolean {
            if (profileId != profile.id) return false
            attemptedProfile = transform(profile)
            throw failure
        }
    }
}
