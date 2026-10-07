// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.server

import app.skipi.app.model.ProxyServerRecord
import features.proxy.server.model.HTTP
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyServerCopyTargetTest {
    @Test
    fun newDraftUsesTemporaryIdAndManualGroupWhenNoGroupWasCaptured() {
        val draft = HTTP(remarks = "Draft")

        val target = ProxyServerEditResult(
            serverId = null,
            server = draft,
        ).toProxyServerCopyTarget(defaultGroupId = DefaultManualGroupId)

        assertEquals(
            ProxyServerRecord(
                id = TemporaryCopyServerId,
                server = draft,
                sourceSubscriptionId = null,
            ),
            target,
        )
    }

    @Test
    fun capturedServerIdAndSourceGroupAreKeptInsteadOfTheReturnGroup() {
        val draft = HTTP(remarks = "Restored draft")

        val target = ProxyServerEditResult(
            serverId = 27,
            server = draft,
            groupId = 83,
            returnGroupId = 0,
        ).toProxyServerCopyTarget(defaultGroupId = DefaultManualGroupId)

        assertEquals(
            ProxyServerRecord(
                id = 27,
                server = draft,
                sourceSubscriptionId = 83,
            ),
            target,
        )
    }

    @Test
    fun capturedAutoBalancerGroupIsKeptForPreview() {
        val draft = HTTP(remarks = "Auto group draft")

        val target = ProxyServerEditResult(
            serverId = null,
            server = draft,
            groupId = AutoBalancerGroupId,
        ).toProxyServerCopyTarget(defaultGroupId = DefaultManualGroupId)

        assertEquals(
            ProxyServerRecord(
                id = TemporaryCopyServerId,
                server = draft,
                sourceSubscriptionId = AutoBalancerGroupId,
            ),
            target,
        )
    }

    @Test
    fun copyTargetReplacesMatchingRowAtItsOriginalPositionWithoutChangingTheInput() {
        val before = listOf(
            CopyTargetTestRow(id = 4, value = "before"),
            CopyTargetTestRow(id = 8, value = "stored"),
            CopyTargetTestRow(id = 12, value = "after"),
        )
        val target = CopyTargetTestRow(id = 8, value = "draft")

        val previewRows = before.withProxyServerCopyTarget(target, CopyTargetTestRow::id)

        assertEquals(
            listOf(
                CopyTargetTestRow(id = 4, value = "before"),
                target,
                CopyTargetTestRow(id = 12, value = "after"),
            ),
            previewRows,
        )
        assertEquals(
            listOf(
                CopyTargetTestRow(id = 4, value = "before"),
                CopyTargetTestRow(id = 8, value = "stored"),
                CopyTargetTestRow(id = 12, value = "after"),
            ),
            before,
        )
    }

    @Test
    fun copyTargetForAnAbsentIdIsAppendedWithoutChangingTheInput() {
        val before = listOf(
            CopyTargetTestRow(id = 4, value = "first"),
            CopyTargetTestRow(id = 12, value = "second"),
        )
        val target = CopyTargetTestRow(id = TemporaryCopyServerId, value = "new draft")

        val previewRows = before.withProxyServerCopyTarget(target, CopyTargetTestRow::id)

        assertEquals(before + target, previewRows)
        assertEquals(
            listOf(
                CopyTargetTestRow(id = 4, value = "first"),
                CopyTargetTestRow(id = 12, value = "second"),
            ),
            before,
        )
    }

    private data class CopyTargetTestRow(val id: Int, val value: String)

    private companion object {
        const val TemporaryCopyServerId = -1
        const val DefaultManualGroupId = 1
        const val AutoBalancerGroupId = -2
    }
}
