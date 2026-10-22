// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.server.createProxyServerEditorDraft
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertIs

class TrafficConfigGlobalProxyGroupEditorResultTest {
    @Test
    fun addDraftUsesGlobalGroupDefaultsRegardlessOfHostDefaultGroup() {
        listOf(1, -1).forEach { hostDefaultGroupId ->
            val result = createProxyServerEditorDraft(
                kind = ProxyHomeServerKind.StrategyGroup,
                defaultGroupId = hostDefaultGroupId,
            )

            assertNull(result.serverId)
            assertEquals(StrategyGroup(), assertIs<StrategyGroup>(result.server))
            assertEquals(DefaultAutoBalancerGroupId, result.groupId)
            assertEquals(DefaultAutoBalancerGroupId, result.returnGroupId)
            assertFalse(result.deleted)
        }
    }

    @Test
    fun editResultKeepsOriginalStrategyGroupAndExplicitServerId() {
        val original = StrategyGroup(remarks = "Keep this instance")

        val existingResult = trafficConfigGlobalProxyGroupEditResult(original, serverId = 307)
        assertNotNull(existingResult)
        assertEquals(307, existingResult.serverId)
        assertSame(original, existingResult.server)
        assertEquals(DefaultAutoBalancerGroupId, existingResult.groupId)
        assertEquals(DefaultAutoBalancerGroupId, existingResult.returnGroupId)
        assertFalse(existingResult.deleted)

        val unsavedResult = trafficConfigGlobalProxyGroupEditResult(original, serverId = null)
        assertNotNull(unsavedResult)
        assertNull(unsavedResult.serverId)
        assertSame(original, unsavedResult.server)
        assertEquals(DefaultAutoBalancerGroupId, unsavedResult.groupId)
        assertEquals(DefaultAutoBalancerGroupId, unsavedResult.returnGroupId)
        assertFalse(unsavedResult.deleted)
    }

    @Test
    fun editResultRejectsNonStrategyServers() {
        assertNull(trafficConfigGlobalProxyGroupEditResult(VLESS(remarks = "ordinary node"), serverId = 8))
    }

    @Test
    fun duplicateResultCopiesOnlyRemarksAndReturnsDetachedUnsavedGroup() {
        val original = StrategyGroup(
            remarks = "Original group",
            strategy = StrategyGroupConstants.TYPE_ROUND_ROBIN,
            subscriptionGroupId = 12,
            filter = "region:east",
            proxyServerIds = listOf(41, 43),
            selectedMemberId = 43,
            displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
            showInAutoBalancerList = false,
            sourceTrafficConfigId = 19,
            sourcePolicyGroupName = "Source policy",
            probeInterval = "17s",
            probeUrl = "https://probe.example.test/status",
            enableBurstProbe = true,
            tolerance = "23ms",
            probeTimeout = "4s",
        )
        val originalBefore = original.encodePersistedProxyServer()
        val copyName = "Localized copy name"

        val result = trafficConfigGlobalProxyGroupDuplicateResult(original, copyName)

        assertNotNull(result)
        assertNull(result.serverId)
        assertEquals(DefaultAutoBalancerGroupId, result.groupId)
        assertEquals(DefaultAutoBalancerGroupId, result.returnGroupId)
        assertFalse(result.deleted)
        val duplicate = assertIs<StrategyGroup>(result.server)
        assertNotSame(original, duplicate)
        assertEquals(copyName, duplicate.remarks)
        assertEquals(
            originalBefore,
            duplicate.copy(remarks = original.remarks).encodePersistedProxyServer(),
        )

        duplicate.filter = "changed in editor"
        duplicate.remarks = "Edited copy"
        assertEquals(originalBefore, original.encodePersistedProxyServer())
    }

    @Test
    fun duplicateResultRejectsNonStrategyServersWithoutChangingThem() {
        val original = ChainProxy(remarks = "ordinary chain", proxyServerIds = listOf(2, 5))
        val originalBefore = original.encodePersistedProxyServer()

        assertNull(trafficConfigGlobalProxyGroupDuplicateResult(original, "Chain copy"))
        assertEquals(originalBefore, original.encodePersistedProxyServer())
    }
}
