// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.store

import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryFeatureStoreTest {
    private data class State(val count: Int = 0)

    @Test
    fun reducerProducesImmutableStateTransitions() {
        val store = InMemoryFeatureStore<State, Int>(State()) { state, increment ->
            state.copy(count = state.count + increment)
        }

        store.dispatch(2)
        store.dispatch(-1)

        assertEquals(State(1), store.state.value)
    }
}
