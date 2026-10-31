// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the Home subscription UI shows about an in-flight or finished subscription update. */
internal class DesktopSubscriptionUiState {
    var url by mutableStateOf("")
    var update by mutableStateOf<DesktopSubscriptionUpdate?>(null)
    var message by mutableStateOf("")
    var updateInProgress by mutableStateOf(false)
    var refreshingId by mutableStateOf<Int?>(null)
    var batchRefreshingIds by mutableStateOf(emptySet<Int>())

    /** Subscriptions being refreshed one-by-one or as part of "refresh all". */
    val refreshingIds: Set<Int> get() = setOfNotNull(refreshingId) + batchRefreshingIds
}
