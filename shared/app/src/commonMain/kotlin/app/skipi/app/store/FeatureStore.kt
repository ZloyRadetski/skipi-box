// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Typed unidirectional contract consumed by shared UI features. */
interface FeatureStore<out State, in Action> {
    val state: StateFlow<State>

    fun dispatch(action: Action)
}

/** Small reducer-backed store useful for common features and deterministic tests. */
class InMemoryFeatureStore<State, Action>(
    initialState: State,
    private val reduce: (State, Action) -> State,
) : FeatureStore<State, Action> {
    private val mutableState = MutableStateFlow(initialState)
    override val state: StateFlow<State> = mutableState.asStateFlow()

    override fun dispatch(action: Action) {
        mutableState.value = reduce(mutableState.value, action)
    }
}
