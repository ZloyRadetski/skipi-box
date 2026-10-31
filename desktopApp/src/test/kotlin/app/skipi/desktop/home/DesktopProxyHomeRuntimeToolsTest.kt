// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeServerTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopProxyHomeRuntimeToolsTest {
    @Test
    fun restartAndDeleteAllEffectsReachConfiguredDesktopOperations() {
        var restartCalls = 0
        var deletedGroupIds: Set<Int>? = null
        val context = DesktopProxyHomeEffectContext(
            currentGroupServerIds = setOf(2, 7),
            onRestartService = { restartCalls++ },
            onDeleteAllServers = { ids -> deletedGroupIds = ids },
        )

        assertTrue(context.handle(ProxyHomeEffect.RunServerTool(ProxyHomeServerTool.RestartService)).isSuccess)
        assertTrue(context.handle(ProxyHomeEffect.RunServerTool(ProxyHomeServerTool.DeleteAllServers)).isSuccess)

        assertEquals(1, restartCalls)
        assertEquals(setOf(2, 7), deletedGroupIds)
    }

    @Test
    fun destructiveToolRequestsTheAndroidStyleConfirmationBeforeDeleting() {
        val requests = mutableListOf<ProxyHomeServerTool>()
        val deletedGroups = mutableListOf<Set<Int>>()
        val context = DesktopProxyHomeEffectContext(
            currentGroupServerIds = setOf(3, 4),
            confirmDeletion = true,
            onConfirmToolDeletion = requests::add,
            onDeleteAllServers = deletedGroups::add,
        )

        assertTrue(context.handle(ProxyHomeEffect.RunServerTool(ProxyHomeServerTool.DeleteAllServers)).isSuccess)

        assertEquals(listOf(ProxyHomeServerTool.DeleteAllServers), requests)
        assertTrue(deletedGroups.isEmpty())
    }

    @Test
    fun latencyCancellationEffectReachesTheRunningDesktopOperation() {
        var cancellationCalls = 0
        val context = DesktopProxyHomeEffectContext(
            onCancelLatencyTests = { cancellationCalls++ },
        )

        assertTrue(context.handle(ProxyHomeEffect.CancelLatencyTests).isSuccess)

        assertEquals(1, cancellationCalls)
    }

    @Test
    fun restartAndDeleteAllAppearOnlyWhenTheirDesktopCallbacksExist() {
        val absent = desktopProxyHomeActionCapabilities(
            subscriptions = emptyList(),
            onRefreshAllSubscriptions = null,
            onDeleteDuplicateServers = null,
            onDeleteInvalidServers = null,
        )
        val available = desktopProxyHomeActionCapabilities(
            subscriptions = emptyList(),
            onRefreshAllSubscriptions = null,
            onDeleteDuplicateServers = null,
            onDeleteInvalidServers = null,
            onRestartService = {},
            onDeleteAllServers = { _ -> },
        )

        assertTrue(ProxyHomeServerTool.RestartService !in absent.serverTools)
        assertTrue(ProxyHomeServerTool.DeleteAllServers !in absent.serverTools)
        assertEquals(
            setOf(ProxyHomeServerTool.RestartService, ProxyHomeServerTool.DeleteAllServers),
            available.serverTools,
        )
    }
}
