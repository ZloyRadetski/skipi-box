// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.store

import app.AppState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The observable, mutable application state that portable use cases work against. The Android host
 * backs it with Room and SharedPreferences; [InMemoryAppStateStore] is the host-free implementation.
 */
interface AppStateStore {
    val state: StateFlow<AppState>

    /** Non-reactive one-shot snapshot for decisions that must not trigger recomposition. */
    val currentState: AppState

    fun update(transform: (AppState) -> AppState)

    /** @param persist false for transient state that must not be written to storage. */
    fun update(persist: Boolean, transform: (AppState) -> AppState)
}

/** [AppStateStore] that keeps everything in memory; used by tests and hosts without persistence. */
class InMemoryAppStateStore(initial: AppState = AppState()) : AppStateStore {
    private val mutableState = MutableStateFlow(initial)

    /** How many updates asked to be persisted; lets a test check the persistence contract. */
    var persistedUpdateCount: Int = 0
        private set

    override val state: StateFlow<AppState> = mutableState.asStateFlow()

    override val currentState: AppState get() = mutableState.value

    override fun update(transform: (AppState) -> AppState) = update(persist = true, transform = transform)

    override fun update(persist: Boolean, transform: (AppState) -> AppState) {
        mutableState.update(transform)
        if (persist) persistedUpdateCount += 1
    }
}
