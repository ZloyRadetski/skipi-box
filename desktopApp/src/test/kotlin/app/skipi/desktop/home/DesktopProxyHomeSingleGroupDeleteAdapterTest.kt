// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.proxy.ProxyServerStopOutcome
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopProxyHomeSingleGroupDeleteAdapterTest {
    @Test
    fun selectedRunningSingleDeleteDispatchesBeforePublishingStoppedState() = runBlocking {
        val events = mutableListOf<String>()

        runDesktopProxyHomeSingleDelete(
            serverId = 7,
            selectedServerId = 7,
            tunnelRunning = true,
            serviceOperationInProgress = false,
            stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
            dispatchDeleteIfPresent = { events += "dispatch"; true },
            applyStoppedState = { events += "stop-state" },
            onDeleted = { events += "deleted" },
            onMissingServer = { events += "missing" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("stop", "dispatch", "stop-state", "deleted"), events)
    }

    @Test
    fun selectedRunningGroupDeleteAwaitsRemovalBeforePublishingStoppedState() = runBlocking {
        val events = mutableListOf<String>()
        val allowRemove = CompletableDeferred<Unit>()
        val removeStarted = CompletableDeferred<Unit>()

        val deletion = async {
            runDesktopProxyHomeSubscriptionGroupDelete(
                groupServerIds = setOf(4, 7),
                selectedServerId = 7,
                tunnelRunning = true,
                serviceOperationInProgress = false,
                groupIsBuiltIn = false,
                stopService = { events += "stop"; ProxyServerStopOutcome.Stopped },
                deleteGroupIfPresent = {
                    events += "remove-start"
                    removeStarted.complete(Unit)
                    allowRemove.await()
                    events += "remove-finished"
                    true
                },
                applyStoppedState = { events += "stop-state" },
                onDeleted = { events += "deleted" },
                onMissingServer = { events += "missing" },
                onStopFailed = { events += "stop-error" },
                onDeleteError = { events += "delete-error" },
            )
        }

        removeStarted.await()
        assertEquals(listOf("stop", "remove-start"), events)
        allowRemove.complete(Unit)
        deletion.await()

        assertEquals(listOf("stop", "remove-start", "remove-finished", "stop-state", "deleted"), events)
    }

    @Test
    fun selectedGroupHomeStopIsNotRepeatedByRepositoryRemovalBeforeUiStatePublishes() = runBlocking {
        val subscriptionId = 5
        var cachedUiRunning = true
        var actualCoreRunning = true
        val events = mutableListOf<String>()
        var serverLibrary = DesktopServerLibrary(
            selectedServerId = 7,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 7,
                    serverJson = testProxyServer().encodePersistedProxyServer(),
                    subscriptionId = subscriptionId,
                ),
            ),
        )
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = serverLibrary,
            readLibrary = { serverLibrary },
            saveLibrary = { events += "server-save"; Result.success(Unit) },
            publishLibrary = { updated -> serverLibrary = updated; events += "server-publish" },
        )
        val removalAdapter = DesktopSubscriptionServerRemovalAdapter(
            readLibrary = { serverLibrary },
            proxyServerRepository = proxyRepository,
            isTunnelRunning = { actualCoreRunning },
            stopSelectedServer = { events += "duplicate-stop" },
        )

        runDesktopProxyHomeSubscriptionGroupDelete(
            groupServerIds = setOf(7),
            selectedServerId = 7,
            tunnelRunning = true,
            serviceOperationInProgress = false,
            groupIsBuiltIn = false,
            stopService = {
                events += "stop-native"
                actualCoreRunning = false
                ProxyServerStopOutcome.Stopped
            },
            deleteGroupIfPresent = {
                check(cachedUiRunning)
                events += "remove"
                removalAdapter.remove(subscriptionId)
                true
            },
            applyStoppedState = {
                events += "stop-state"
                cachedUiRunning = false
            },
            onDeleted = { events += "deleted" },
            onMissingServer = { events += "missing" },
            onStopFailed = { events += "stop-error" },
            onDeleteError = { events += "delete-error" },
        )

        assertEquals(
            listOf("stop-native", "remove", "server-save", "server-publish", "stop-state", "deleted"),
            events,
        )
        assertEquals(false, actualCoreRunning)
        assertEquals(false, cachedUiRunning)
        assertEquals(null, serverLibrary.selectedServerId)
    }

    private fun testProxyServer() = features.proxy.server.model.ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@server.example:443?security=tls#Server",
    )
}
