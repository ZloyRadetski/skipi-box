// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import app.skipi.app.server.ProxyServerEditResult
import features.config.ProxyGroupServerChoice
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.toShadowrocketLine
import features.config.withShadowrocketProxyGroupAdded
import features.config.withShadowrocketProxyGroupLine
import features.config.withoutShadowrocketProxyGroupLine
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrafficConfigProxyGroupEditorControllerTest {
    @Test
    fun deleteProxyGroupUsesCapturedDocumentOnLatestSameIdProfile() {
        val opened = profile(41, "Opened")
        val groupLineNumber = opened.rawConfig.analyzeShadowrocketConfig().proxyGroups.first().lineNumber
        val latest = opened.copy(
            name = "Latest metadata",
            sourceUrl = "https://latest.example/profile.conf",
            updateLocked = true,
            rawConfig = opened.rawConfig.replace("vendor-option = opened", "vendor-option = latest"),
        )
        val sibling = profile(42, "Sibling")
        val store = InMemoryProfileStore(latest, sibling)
        val controller = TrafficConfigEditorController(store)
        val expectedDocument = opened.rawConfig.withoutShadowrocketProxyGroupLine(groupLineNumber)

        val result = controller.deleteProxyGroup(opened, groupLineNumber)

        assertIs<TrafficConfigEditorResult.Saved>(result)
        val saved = store.profile(opened.id)!!
        assertEquals(expectedDocument, saved.rawConfig)
        assertEquals("Latest metadata", saved.name)
        assertEquals("https://latest.example/profile.conf", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertFalse(saved.rawConfig.contains("vendor-option = latest"))
        assertEquals(sibling, store.profile(sibling.id))
    }

    @Test
    fun deletedResultRemovesCapturedGroupByPositiveProfileLineNumber() {
        val opened = profile(43, "Delete result")
        val groupLineNumber = opened.rawConfig.analyzeShadowrocketConfig().proxyGroups.first().lineNumber
        val store = InMemoryProfileStore(opened)
        val controller = TrafficConfigEditorController(store)
        val expectedDocument = opened.rawConfig.withoutShadowrocketProxyGroupLine(groupLineNumber)

        val result = controller.saveProxyGroupResult(
            profile = opened,
            result = ProxyServerEditResult(
                serverId = groupLineNumber,
                server = StrategyGroup(),
                deleted = true,
            ),
            serverChoices = emptyList(),
        )

        assertIs<TrafficConfigEditorResult.Saved>(result)
        assertEquals(expectedDocument, store.profile(opened.id)!!.rawConfig)
    }

    @Test
    fun editResultTreatsServerIdAsProfileLineNumberEvenWhenItMatchesMemberId() {
        val opened = profile(44, "Edit result")
        val groups = opened.rawConfig.analyzeShadowrocketConfig().proxyGroups
        val editedGroup = groups.first()
        val collisionId = editedGroup.lineNumber
        val latest = opened.copy(
            name = "Latest edit metadata",
            rawConfig = opened.rawConfig.replace("vendor-option = opened", "vendor-option = latest"),
        )
        val strategy = StrategyGroup(
            remarks = "Renamed",
            strategy = StrategyGroupConstants.TYPE_FALLBACK,
            proxyServerIds = listOf(collisionId, 700),
        )
        val choices = listOf(
            ProxyGroupServerChoice(id = collisionId, rawName = "Same-number catalog server"),
            ProxyGroupServerChoice(id = 700, rawName = "Other member"),
        )
        val expectedDocument = opened.rawConfig.withShadowrocketProxyGroupLine(
            collisionId,
            strategy.toShadowrocketLine(choices),
        )
        val store = InMemoryProfileStore(latest)
        val controller = TrafficConfigEditorController(store)

        val result = controller.saveProxyGroupResult(
            profile = opened,
            result = ProxyServerEditResult(serverId = collisionId, server = strategy),
            serverChoices = choices,
        )

        assertIs<TrafficConfigEditorResult.Saved>(result)
        val savedRaw = store.profile(opened.id)!!.rawConfig
        assertEquals(expectedDocument, savedRaw)
        assertEquals("Latest edit metadata", store.profile(opened.id)!!.name)
        assertFalse(savedRaw.contains("vendor-option = latest"))
        val savedGroups = savedRaw.analyzeShadowrocketConfig().proxyGroups
        assertEquals("Renamed", savedGroups[0].name)
        assertEquals("Other", savedGroups[1].name)
        assertTrue(savedGroups[0].raw.contains("Same-number catalog server"))
    }

    @Test
    fun nullOrNonPositiveEditResultIdAddsGroupToCapturedDocument() {
        val opened = profile(45, "Add result")
        val strategy = StrategyGroup(
            remarks = "Added",
            strategy = StrategyGroupConstants.TYPE_SELECT,
            proxyServerIds = listOf(701),
        )
        val choices = listOf(ProxyGroupServerChoice(id = 701, rawName = "New member"))
        val expectedDocument = opened.rawConfig.withShadowrocketProxyGroupAdded(strategy.toShadowrocketLine(choices))
        listOf<Int?>(null, 0, -1).forEachIndexed { index, resultId ->
            val currentProfile = opened.copy(id = opened.id + index)
            val store = InMemoryProfileStore(currentProfile)
            val controller = TrafficConfigEditorController(store)

            val result = controller.saveProxyGroupResult(
                profile = currentProfile,
                result = ProxyServerEditResult(serverId = resultId, server = strategy),
                serverChoices = choices,
            )

            assertIs<TrafficConfigEditorResult.Saved>(result)
            assertEquals(expectedDocument, store.profile(currentProfile.id)!!.rawConfig)
        }
    }

    @Test
    fun invalidDeleteNonStrategyAndBlankNameResultsSkipWithoutWriting() {
        val opened = profile(46, "Invalid results")
        val store = InMemoryProfileStore(opened)
        val controller = TrafficConfigEditorController(store)
        val choices = emptyList<ProxyGroupServerChoice>()

        val invalidDelete = controller.saveProxyGroupResult(
            profile = opened,
            result = ProxyServerEditResult(serverId = 0, server = StrategyGroup(), deleted = true),
            serverChoices = choices,
        )
        val missingDeleteId = controller.saveProxyGroupResult(
            profile = opened,
            result = ProxyServerEditResult(serverId = null, server = StrategyGroup(), deleted = true),
            serverChoices = choices,
        )
        val nonStrategy = controller.saveProxyGroupResult(
            profile = opened,
            result = ProxyServerEditResult(serverId = 1, server = VLESS(remarks = "VLESS")),
            serverChoices = choices,
        )
        val blankName = controller.saveProxyGroupResult(
            profile = opened,
            result = ProxyServerEditResult(serverId = -1, server = StrategyGroup(remarks = "  ")),
            serverChoices = choices,
        )
        assertIs<TrafficConfigEditorResult.Skipped>(invalidDelete)
        assertIs<TrafficConfigEditorResult.Skipped>(missingDeleteId)
        assertIs<TrafficConfigEditorResult.Skipped>(nonStrategy)
        assertIs<TrafficConfigEditorResult.Skipped>(blankName)
        assertEquals(0, store.updateCalls)
        assertEquals(opened, store.profile(opened.id))
    }

    @Test
    fun resultDoesNotResurrectMissingProfile() {
        val opened = profile(47, "Missing")
        val store = InMemoryProfileStore()
        val controller = TrafficConfigEditorController(store)
        val strategy = StrategyGroup(remarks = "New group")

        val result = controller.saveProxyGroupResult(
            profile = opened,
            result = ProxyServerEditResult(serverId = -1, server = strategy),
            serverChoices = emptyList(),
        )

        assertIs<TrafficConfigEditorResult.Missing>(result)
        assertTrue(store.profiles.isEmpty())
    }

    @Test
    fun profileStoreFailureIsReportedWithoutClaimingSuccess() {
        val opened = profile(48, "Failure")
        val failure = IllegalStateException("profile write failed")
        val store = FailingProfileStore(failure)
        var reported: Exception? = null
        val controller = TrafficConfigEditorController(store, onSaveFailure = { reported = it })
        val groupLineNumber = opened.rawConfig.analyzeShadowrocketConfig().proxyGroups.first().lineNumber

        val result = controller.deleteProxyGroup(opened, groupLineNumber)

        val failed = assertIs<TrafficConfigEditorResult.Failed>(result)
        assertSame(failure, failed.cause)
        assertSame(failure, reported)
    }

    private fun profile(id: Int, name: String): TrafficConfigState = TrafficConfigState(
        id = id,
        name = name,
        sourceUrl = "https://opened.example/profile.conf",
        updateLocked = false,
        rawConfig = """# external comment
[General]
ipv6 = true

[Proxy Group]
Existing = select, Old member, skipi-display=always
Other = select, Kept member, skipi-display=always

[Vendor]
vendor-option = opened
""",
    )

    private class InMemoryProfileStore(vararg initialProfiles: TrafficConfigState) : TrafficConfigEditorProfileStore {
        val profiles = linkedMapOf(*initialProfiles.map { it.id to it }.toTypedArray())
        var updateCalls = 0

        override fun update(
            profileId: Int,
            transform: (TrafficConfigState) -> TrafficConfigState,
        ): Boolean {
            updateCalls += 1
            val current = profiles[profileId] ?: return false
            profiles[profileId] = transform(current)
            return true
        }

        fun profile(id: Int): TrafficConfigState? = profiles[id]
    }

    private class FailingProfileStore(private val failure: Exception) : TrafficConfigEditorProfileStore {
        override fun update(
            profileId: Int,
            transform: (TrafficConfigState) -> TrafficConfigState,
        ): Boolean = throw failure
    }
}
