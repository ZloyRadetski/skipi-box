// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import app.skipi.app.store.AppStateStore
import features.updater.AppUpdateState

/**
 * For builds without self-update. A build with it, installed earlier, may have left scheduled work and a saved
 * offer behind (an APK with the same signature installs over it).
 */
internal fun retireSelfUpdate(stateStore: AppStateStore, cancelWork: () -> Unit) {
    cancelWork()
    if (stateStore.currentState.appUpdateState() != AppUpdateState()) {
        stateStore.update { state -> state.updatingAppUpdate { cleared() } }
    }
}
