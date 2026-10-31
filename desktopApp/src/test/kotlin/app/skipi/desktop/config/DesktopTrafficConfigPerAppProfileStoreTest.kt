// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeWhitelist
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorResult
import app.skipi.app.config.TrafficConfigProxyAppListImportResult
import app.skipi.app.config.toTrafficConfigState
import features.clipboard.ClipboardImportMode
import features.config.ConfigProfile
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import features.proxy.app.usecase.ProxyAppListClipboardData
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopTrafficConfigPerAppProfileStoreTest {
    @Test
    fun modeAndSelectionChangesPersistImmediatelyWithoutChangingLibraryEnvelope() = withFileBackedController(
        initialLibrary = initialLibrary(),
    ) { configsPath, controller ->
        val initial = loadLibrary(configsPath)
        val initialTarget = initial.configs.single { it.id == TargetProfileId }
        val initialState = initialTarget.toTrafficConfigState(ResourceUserAgentFallback)

        assertIs<TrafficConfigEditorResult.Saved>(
            controller.setProxyAppListMode(TargetProfileId, ProxyAppListModeWhitelist),
        )

        val afterModeChange = loadLibrary(configsPath)
        assertLibraryEnvelopePreserved(afterModeChange, initial)
        val modeChangedState = afterModeChange.configs.single { it.id == TargetProfileId }
            .toTrafficConfigState(ResourceUserAgentFallback)
        assertEquals(ProxyAppListModeWhitelist, modeChangedState.proxyAppListMode)
        assertEquals(initialState.proxyAppListSelectedApps, modeChangedState.proxyAppListSelectedApps)
        assertTargetSectionsPreserved(
            afterModeChange.configs.single { it.id == TargetProfileId },
            proxyServer = "target.example:443",
            rule = "DOMAIN,target.example,PROXY",
            vendorValue = "target-vendor-preserved",
        )

        val sharedUidSelectionKeys = listOf(
            "0:com.example.shared.first",
            "0:com.example.shared.second",
            "0:com.example.shared.first",
        )
        assertIs<TrafficConfigEditorResult.Saved>(
            controller.setProxyAppListSelection(
                profileId = TargetProfileId,
                selectionKeys = sharedUidSelectionKeys,
                isChecked = true,
            ),
        )

        val afterCheck = loadLibrary(configsPath)
        assertLibraryEnvelopePreserved(afterCheck, initial)
        assertEquals(
            listOf(
                "legacy.unknown",
                "0:com.example.shared.first",
                "1:com.example.other-user",
                "0:com.example.shared.second",
            ),
            afterCheck.configs.single { it.id == TargetProfileId }
                .toTrafficConfigState(ResourceUserAgentFallback).proxyAppListSelectedApps,
        )
        assertTargetSectionsPreserved(
            afterCheck.configs.single { it.id == TargetProfileId },
            proxyServer = "target.example:443",
            rule = "DOMAIN,target.example,PROXY",
            vendorValue = "target-vendor-preserved",
        )

        assertIs<TrafficConfigEditorResult.Saved>(
            controller.setProxyAppListSelection(
                profileId = TargetProfileId,
                selectionKeys = sharedUidSelectionKeys,
                isChecked = false,
            ),
        )

        val afterUncheck = loadLibrary(configsPath)
        assertLibraryEnvelopePreserved(afterUncheck, initial)
        val uncheckedState = afterUncheck.configs.single { it.id == TargetProfileId }
            .toTrafficConfigState(ResourceUserAgentFallback)
        assertEquals(listOf("legacy.unknown", "1:com.example.other-user"), uncheckedState.proxyAppListSelectedApps)
        assertEquals(ProxyAppListModeWhitelist, uncheckedState.proxyAppListMode)
        assertTargetSectionsPreserved(
            afterUncheck.configs.single { it.id == TargetProfileId },
            proxyServer = "target.example:443",
            rule = "DOMAIN,target.example,PROXY",
            vendorValue = "target-vendor-preserved",
        )
    }

    @Test
    fun mergeUsesLatestSameIdProfileAndLateSiblingFromThePersistedLibrary() = withFileBackedController(
        initialLibrary = initialLibrary(),
    ) { configsPath, controller ->
        val initial = loadLibrary(configsPath)
        val latestTarget = profile(
            id = TargetProfileId,
            name = "Latest target edit",
            sourceUrl = "https://latest.example/target.conf",
            updateLocked = true,
            lastUpdatedAtMillis = 991L,
            proxyServer = "late-target.example:8443",
            rule = "DOMAIN,late-target.example,DIRECT",
            vendorValue = "late-target-preserved",
            mode = ProxyAppListModeBlacklist,
            selectedApps = listOf("legacy.unknown", "0:com.example.existing"),
        )
        val latestSibling = profile(
            id = LateSiblingProfileId,
            name = "Late sibling",
            sourceUrl = "https://late-sibling.example/profile.conf",
            updateLocked = false,
            lastUpdatedAtMillis = 612L,
            proxyServer = "late-sibling.example:443",
            rule = "DOMAIN,late-sibling.example,PROXY",
            vendorValue = "late-sibling-preserved",
            selectedApps = listOf("legacy.late-sibling"),
        )
        val latestLibrary = initial.copy(
            selectedConfigId = SelectedSiblingId,
            configs = listOf(
                latestTarget,
                initial.configs.single { it.id == SelectedSiblingId },
                latestSibling,
            ),
            nextConfigId = LatestNextConfigId,
        )

        // Simulate a profile edit and a new sibling arriving after this controller opened.
        DesktopConfigLibraries.save(configsPath, latestLibrary).getOrThrow()

        val outcome: TrafficConfigProxyAppListImportResult = controller.applyProxyAppListImport(
            profileId = TargetProfileId,
            imported = ProxyAppListClipboardData(
                mode = null,
                selectedApps = listOf(
                    "0:com.example.new",
                    "10:com.example.other-user",
                    "0:com.example.new",
                    "0:com.example.existing",
                ),
            ),
            importMode = ClipboardImportMode.Merge,
        )

        assertIs<TrafficConfigEditorResult.Saved>(outcome.updateResult)
        assertEquals(2, outcome.importedCount)

        val savedLibrary = loadLibrary(configsPath)
        assertEquals(SelectedSiblingId, savedLibrary.selectedConfigId)
        assertEquals(LatestNextConfigId, savedLibrary.nextConfigId)
        assertEquals(
            listOf(TargetProfileId, SelectedSiblingId, LateSiblingProfileId),
            savedLibrary.configs.map(ConfigProfile::id),
        )
        assertEquals(
            latestLibrary.configs.single { it.id == SelectedSiblingId },
            savedLibrary.configs.single { it.id == SelectedSiblingId },
        )
        assertEquals(latestSibling, savedLibrary.configs.single { it.id == LateSiblingProfileId })

        val savedTarget = savedLibrary.configs.single { it.id == TargetProfileId }
        assertEquals(latestTarget.name, savedTarget.name)
        assertEquals(latestTarget.sourceUrl, savedTarget.sourceUrl)
        assertEquals(latestTarget.updateLocked, savedTarget.updateLocked)
        assertEquals(latestTarget.lastUpdatedAtMillis, savedTarget.lastUpdatedAtMillis)
        val savedTargetState = savedTarget.toTrafficConfigState(ResourceUserAgentFallback)
        assertEquals(ProxyAppListModeBlacklist, savedTargetState.proxyAppListMode)
        assertEquals(
            listOf(
                "legacy.unknown",
                "0:com.example.existing",
                "0:com.example.new",
                "10:com.example.other-user",
            ),
            savedTargetState.proxyAppListSelectedApps,
        )
        assertTargetSectionsPreserved(
            savedTarget,
            proxyServer = "late-target.example:8443",
            rule = "DOMAIN,late-target.example,DIRECT",
            vendorValue = "late-target-preserved",
        )
    }

    private fun initialLibrary(): DesktopConfigLibrary {
        val target = profile(
            id = TargetProfileId,
            name = "Target profile",
            sourceUrl = "https://target.example/profile.conf",
            updateLocked = false,
            lastUpdatedAtMillis = 345L,
            proxyServer = "target.example:443",
            rule = "DOMAIN,target.example,PROXY",
            vendorValue = "target-vendor-preserved",
            mode = ProxyAppListModeBlacklist,
            selectedApps = listOf(
                "legacy.unknown",
                "0:com.example.shared.first",
                "1:com.example.other-user",
            ),
        )
        val sibling = profile(
            id = SelectedSiblingId,
            name = "Selected sibling",
            sourceUrl = "https://sibling.example/profile.conf",
            updateLocked = true,
            lastUpdatedAtMillis = 456L,
            proxyServer = "sibling.example:443",
            rule = "DOMAIN,sibling.example,DIRECT",
            vendorValue = "sibling-vendor-preserved",
            selectedApps = listOf("legacy.sibling"),
        )
        return DesktopConfigLibrary(
            selectedConfigId = SelectedSiblingId,
            configs = listOf(target, sibling),
            nextConfigId = InitialNextConfigId,
        )
    }

    private fun profile(
        id: Int,
        name: String,
        sourceUrl: String,
        updateLocked: Boolean,
        lastUpdatedAtMillis: Long,
        proxyServer: String,
        rule: String,
        vendorValue: String,
        mode: Int = ProxyAppListModeWhitelist,
        selectedApps: List<String> = emptyList(),
    ): ConfigProfile {
        val state = TrafficConfigState(
            id = id,
            name = name,
            rawConfig = """[General]
loglevel = warning

[Proxy]
server = $proxyServer

[Rule]
$rule

[Vendor]
vendor-option = $vendorValue
""",
            sourceUrl = sourceUrl,
            updateLocked = updateLocked,
            lastUpdatedAtMillis = lastUpdatedAtMillis,
            proxyAppListMode = mode,
            proxyAppListSelectedApps = selectedApps,
        ).withSkipiSettingsInRawConfig()
        return ConfigProfile(
            id = id,
            name = state.name,
            content = state.rawConfig,
            sourceUrl = state.sourceUrl,
            updateLocked = state.updateLocked,
            lastUpdatedAtMillis = state.lastUpdatedAtMillis,
        )
    }

    private fun assertLibraryEnvelopePreserved(actual: DesktopConfigLibrary, expected: DesktopConfigLibrary) {
        assertEquals(expected.selectedConfigId, actual.selectedConfigId)
        assertEquals(expected.nextConfigId, actual.nextConfigId)
        assertEquals(expected.configs.map(ConfigProfile::id), actual.configs.map(ConfigProfile::id))
        assertEquals(
            expected.configs.single { it.id == SelectedSiblingId },
            actual.configs.single { it.id == SelectedSiblingId },
        )

        val expectedTarget = expected.configs.single { it.id == TargetProfileId }
        val actualTarget = actual.configs.single { it.id == TargetProfileId }
        assertEquals(expectedTarget.name, actualTarget.name)
        assertEquals(expectedTarget.sourceUrl, actualTarget.sourceUrl)
        assertEquals(expectedTarget.updateLocked, actualTarget.updateLocked)
        assertEquals(expectedTarget.lastUpdatedAtMillis, actualTarget.lastUpdatedAtMillis)
    }

    private fun assertTargetSectionsPreserved(
        profile: ConfigProfile,
        proxyServer: String,
        rule: String,
        vendorValue: String,
    ) {
        assertTrue(profile.content.contains("[Proxy]"))
        assertTrue(profile.content.contains("server = $proxyServer"))
        assertTrue(profile.content.contains("[Rule]"))
        assertTrue(profile.content.contains(rule))
        assertTrue(profile.content.contains("[Vendor]"))
        assertTrue(profile.content.contains("vendor-option = $vendorValue"))
    }

    private fun withFileBackedController(
        initialLibrary: DesktopConfigLibrary,
        block: (configsPath: Path, controller: TrafficConfigEditorController) -> Unit,
    ) {
        val tempDirectory = Files.createTempDirectory("desktop-per-app-profile-store-")
        try {
            val configsPath = tempDirectory.resolve("configs.json")
            DesktopConfigLibraries.save(configsPath, initialLibrary).getOrThrow()
            val repository = DesktopTrafficConfigRepository(
                initialLibrary = initialLibrary,
                readLibrary = { loadLibrary(configsPath) },
                saveLibrary = { DesktopConfigLibraries.save(configsPath, it) },
                publishLibrary = {},
            )
            val store = DesktopTrafficConfigEditorProfileStore(
                repository = repository,
                resourceUserAgentFallback = ResourceUserAgentFallback,
            )
            block(configsPath, TrafficConfigEditorController(store))
        } finally {
            Files.walk(tempDirectory).use { paths ->
                paths.sorted(java.util.Comparator.reverseOrder<Path>()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun loadLibrary(path: Path): DesktopConfigLibrary =
        DesktopConfigLibraries.load(path).getOrThrow()

    private companion object {
        const val TargetProfileId = 7
        const val SelectedSiblingId = 3
        const val LateSiblingProfileId = 15
        const val InitialNextConfigId = 29
        const val LatestNextConfigId = 40
        const val ResourceUserAgentFallback = "Skipi/test/Desktop"
    }
}
