// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.store

import app.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Portable use cases read and change the application state only through [AppStateStore]. */
class InMemoryAppStateStoreTest {
    @Test
    fun currentStateAndTheStateFlowStartAtTheInitialState() {
        val initial = AppState(proxyRunning = true)

        val store = InMemoryAppStateStore(initial)

        assertEquals(initial, store.currentState)
        assertEquals(initial, store.state.value)
    }

    @Test
    fun updateAppliesTheTransformAndPublishesTheResult() {
        val store = InMemoryAppStateStore()

        store.update { it.copy(proxyRunning = true) }

        assertTrue(store.currentState.proxyRunning)
        assertTrue(store.state.value.proxyRunning)
    }

    @Test
    fun updatesAreAppliedInCallOrder() {
        val store = InMemoryAppStateStore()

        store.update { it.copy(selectedProxyServerId = 1) }
        store.update { it.copy(selectedProxyServerId = it.selectedProxyServerId + 1) }

        assertEquals(2, store.currentState.selectedProxyServerId)
    }

    @Test
    fun theNonPersistingOverloadStillChangesTheInMemoryState() {
        val store = InMemoryAppStateStore()

        store.update(persist = false) { it.copy(proxyRunning = true) }

        assertTrue(store.currentState.proxyRunning)
        assertFalse(store.persistedUpdateCount > 0)
    }

    @Test
    fun persistingUpdatesAreCountedSoHostsCanBeVerified() {
        val store = InMemoryAppStateStore()

        store.update { it }
        store.update(persist = true) { it }
        store.update(persist = false) { it }

        assertEquals(2, store.persistedUpdateCount)
    }
}
