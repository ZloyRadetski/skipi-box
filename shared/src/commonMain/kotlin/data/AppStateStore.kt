// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import app.AppState
import kotlinx.coroutines.flow.StateFlow

interface AppStateStore {
    val state: StateFlow<AppState>
    fun update(transform: (AppState) -> AppState)
    suspend fun resetToStockState()
}
