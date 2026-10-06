// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import app.skipi.app.store.SharedApplicationActionOutcome
import features.proxy.server.usecase.ProxyServiceResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProxyServerListTopBarDeletionTest {
    @Test
    fun failedCatalogDeleteSynchronizesStoppedRuntimeBeforeReportingError() = runTest {
        val events = mutableListOf<String>()
        var state = AppState(proxyRunning = true, localProxyPort = "1080")
        var deleteError: Throwable? = null
        var wasDeleted = false

        deleteProxyServersAfterStoppingService(
            stopService = {
                events += "stop"
                ProxyServiceResult.Success(
                    proxyRunning = false,
                    appState = AppState(proxyRunning = false, localProxyPort = "12080"),
                )
            },
            dispatchDelete = {
                events += "delete"
                SharedApplicationActionOutcome.Failed("catalog save failed")
            },
            applyStopState = { result ->
                events += "runtime"
                state = state.copy(
                    proxyRunning = result.proxyRunning,
                    localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                )
            },
            onDeleted = {
                events += "deleted"
                wasDeleted = true
            },
            onDeleteError = { error ->
                events += "delete-error"
                deleteError = error
            },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("stop", "runtime", "delete", "delete-error"), events)
        assertFalse(state.proxyRunning)
        assertEquals("12080", state.localProxyPort)
        assertIs<IllegalStateException>(deleteError)
        assertEquals("catalog save failed", deleteError?.message)
        assertFalse(wasDeleted)
    }

    @Test
    fun rejectedCatalogDeleteSynchronizesStoppedRuntimeBeforeReportingError() = runTest {
        val events = mutableListOf<String>()
        var state = AppState(proxyRunning = true, localProxyPort = "1080")
        var deleteError: Throwable? = null
        var wasDeleted = false

        deleteProxyServersAfterStoppingService(
            stopService = {
                events += "stop"
                ProxyServiceResult.Success(
                    proxyRunning = false,
                    appState = AppState(proxyRunning = false, localProxyPort = "12081"),
                )
            },
            dispatchDelete = {
                events += "delete"
                SharedApplicationActionOutcome.Rejected("catalog rejected")
            },
            applyStopState = { result ->
                events += "runtime"
                state = state.copy(
                    proxyRunning = result.proxyRunning,
                    localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                )
            },
            onDeleted = {
                events += "deleted"
                wasDeleted = true
            },
            onDeleteError = { error ->
                events += "delete-error"
                deleteError = error
            },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("stop", "runtime", "delete", "delete-error"), events)
        assertFalse(state.proxyRunning)
        assertEquals("12081", state.localProxyPort)
        assertIs<IllegalArgumentException>(deleteError)
        assertEquals("catalog rejected", deleteError?.message)
        assertFalse(wasDeleted)
    }

    @Test
    fun failedServiceStopDoesNotAttemptCatalogDeleteOrReportDeletion() = runTest {
        val events = mutableListOf<String>()
        val stopError = IllegalStateException("service did not stop")
        var state = AppState(proxyRunning = true, localProxyPort = "1080")
        var deleteError: Throwable? = null
        var reportedStopError: Throwable? = null
        var wasDeleted = false

        deleteProxyServersAfterStoppingService(
            stopService = {
                events += "stop"
                ProxyServiceResult.Failed(stopError)
            },
            dispatchDelete = {
                events += "delete"
                SharedApplicationActionOutcome.Completed
            },
            applyStopState = { result ->
                events += "runtime"
                state = state.copy(
                    proxyRunning = result.proxyRunning,
                    localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                )
            },
            onDeleted = {
                events += "deleted"
                wasDeleted = true
            },
            onDeleteError = { deleteError = it },
            onStopFailed = { error ->
                events += "stop-error"
                reportedStopError = error
            },
        )

        assertEquals(listOf("stop", "stop-error"), events)
        assertTrue(state.proxyRunning)
        assertEquals("1080", state.localProxyPort)
        assertSame(stopError, reportedStopError)
        assertNull(deleteError)
        assertFalse(wasDeleted)
    }

    @Test
    fun missingServerStillAttemptsAndReportsSuccessfulCatalogDelete() = runTest {
        val events = mutableListOf<String>()
        var state = AppState(proxyRunning = true, localProxyPort = "1080")
        var deleteError: Throwable? = null
        var stopError: Throwable? = null
        var wasDeleted = false

        deleteProxyServersAfterStoppingService(
            stopService = {
                events += "stop"
                ProxyServiceResult.MissingServer
            },
            dispatchDelete = {
                events += "delete"
                SharedApplicationActionOutcome.Completed
            },
            applyStopState = { result ->
                events += "runtime"
                state = state.copy(
                    proxyRunning = result.proxyRunning,
                    localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                )
            },
            onDeleted = {
                events += "deleted"
                state = state.copy(proxyRunning = false)
                wasDeleted = true
            },
            onDeleteError = { deleteError = it },
            onStopFailed = { stopError = it },
        )

        assertEquals(listOf("stop", "delete", "deleted"), events)
        assertFalse(state.proxyRunning)
        assertEquals("1080", state.localProxyPort)
        assertNull(deleteError)
        assertNull(stopError)
        assertTrue(wasDeleted)
    }

    @Test
    fun successfulStopAndDeleteSynchronizeRuntimeBeforeReportingDeletion() = runTest {
        val events = mutableListOf<String>()
        var state = AppState(proxyRunning = true, localProxyPort = "1080")
        var wasDeleted = false

        deleteProxyServersAfterStoppingService(
            stopService = {
                events += "stop"
                ProxyServiceResult.Success(
                    proxyRunning = false,
                    appState = AppState(proxyRunning = false, localProxyPort = "12082"),
                )
            },
            dispatchDelete = {
                events += "delete"
                SharedApplicationActionOutcome.Completed
            },
            applyStopState = { result ->
                events += "runtime"
                state = state.copy(
                    proxyRunning = result.proxyRunning,
                    localProxyPort = result.appState?.localProxyPort ?: state.localProxyPort,
                )
            },
            onDeleted = {
                events += "deleted"
                wasDeleted = true
            },
            onDeleteError = { events += "delete-error" },
            onStopFailed = { events += "stop-error" },
        )

        assertEquals(listOf("stop", "runtime", "delete", "deleted"), events)
        assertFalse(state.proxyRunning)
        assertEquals("12082", state.localProxyPort)
        assertTrue(wasDeleted)
    }
}
