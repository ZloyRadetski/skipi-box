// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.server.ProxyServerEditApplyOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopProxyHomeSaveFeedbackTest {
    @Test
    fun successfulSaveMapsServerGroupIdsToHomeGroupIds() {
        assertEquals("manual", savedGroup(1))
        assertEquals("auto-balancers", savedGroup(-2))
        assertEquals("all", savedGroup(0))
        assertEquals("subscription:73", savedGroup(73))
    }

    @Test
    fun currentHomeGroupCanBeCapturedForTheEditorReturnRoute() {
        assertEquals(1, desktopProxyServerGroupIdForHomeGroup("manual"))
        assertEquals(-2, desktopProxyServerGroupIdForHomeGroup("auto-balancers"))
        assertEquals(0, desktopProxyServerGroupIdForHomeGroup("all"))
        assertEquals(73, desktopProxyServerGroupIdForHomeGroup("subscription:73"))
        assertNull(desktopProxyServerGroupIdForHomeGroup(null))
        assertNull(desktopProxyServerGroupIdForHomeGroup("subscription:0"))
        assertNull(desktopProxyServerGroupIdForHomeGroup("unknown"))
    }

    @Test
    fun failedDeletedOrUnmappedSaveOutcomesDoNotSelectAHomeGroup() {
        assertNull(
            ProxyServerEditApplyOutcome.Failed("write failed").toDesktopProxyHomeGroupId(),
        )
        assertNull(ProxyServerEditApplyOutcome.Deleted.toDesktopProxyHomeGroupId())
        assertNull(
            ProxyServerEditApplyOutcome.Saved(
                serverId = 8,
                wasExistingAtCommit = false,
                selectedGroupId = null,
            ).toDesktopProxyHomeGroupId(),
        )
    }

    @Test
    fun unavailableSavedGroupStaysPendingUntilItAppearsThenSelectsOnce() {
        val firstResolution = resolvePendingDesktopProxyHomeGroupSelection(
            pendingGroupId = savedGroup(1),
            availableGroupIds = setOf("all"),
        )

        assertNull(firstResolution.groupToSelect)
        assertEquals("manual", firstResolution.pendingGroupId)

        val secondResolution = resolvePendingDesktopProxyHomeGroupSelection(
            pendingGroupId = firstResolution.pendingGroupId,
            availableGroupIds = setOf("all", "manual"),
        )

        assertEquals("manual", secondResolution.groupToSelect)
        assertNull(secondResolution.pendingGroupId)

        val afterSelection = resolvePendingDesktopProxyHomeGroupSelection(
            pendingGroupId = secondResolution.pendingGroupId,
            availableGroupIds = setOf("all", "manual"),
        )
        assertNull(afterSelection.groupToSelect)
        assertNull(afterSelection.pendingGroupId)
    }

    @Test
    fun nullPendingGroupIsANoop() {
        val resolution = resolvePendingDesktopProxyHomeGroupSelection(
            pendingGroupId = null,
            availableGroupIds = setOf("all", "manual"),
        )

        assertNull(resolution.groupToSelect)
        assertNull(resolution.pendingGroupId)
    }

    private fun savedGroup(groupId: Int) = ProxyServerEditApplyOutcome.Saved(
        serverId = 27,
        wasExistingAtCommit = false,
        selectedGroupId = groupId,
    ).toDesktopProxyHomeGroupId()
}
