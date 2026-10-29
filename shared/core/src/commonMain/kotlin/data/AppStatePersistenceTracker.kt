// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import app.AppState

class AppStatePersistenceTracker(
    initialPersistedState: AppState,
) {
    private var lastPersistedState = initialPersistedState

    fun plan(nextState: AppState, hasPersistedRoomState: Boolean): AppStatePersistencePlan {
        return AppStatePersistencePlan(
            previousState = lastPersistedState,
            nextState = nextState,
            replaceAll = !hasPersistedRoomState,
        )
    }

    fun markPersisted(state: AppState) {
        lastPersistedState = state
    }
}

data class AppStatePersistencePlan(
    val previousState: AppState,
    val nextState: AppState,
    val replaceAll: Boolean,
)
