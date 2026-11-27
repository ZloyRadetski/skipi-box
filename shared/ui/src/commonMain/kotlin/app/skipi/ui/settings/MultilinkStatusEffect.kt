// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import features.logs.AppLog
import features.multilink.MultilinkOffDelayMillis
import features.multilink.MultilinkSettings
import features.multilink.MultilinkStatus
import features.multilink.MultilinkStatusEveryMillis
import features.multilink.MultilinkSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Applies [settings] to Core while [tunnelRunning] and returns Core's view of the links, refreshed every second while
 * merging is on. A refused setting goes to [onError]. Desktop hosts the link merging page with this; Android's
 * tunnel service applies the settings itself and the page only reads (see [rememberMultilinkCoreStatus]).
 */
@Composable
fun rememberMultilinkStatus(
    multilink: MultilinkSync,
    settings: MultilinkSettings,
    tunnelRunning: Boolean,
    onError: (Throwable) -> Unit,
): State<MultilinkStatus> {
    LaunchedEffect(multilink, settings, tunnelRunning) {
        if (!tunnelRunning) delay(MultilinkOffDelayMillis)
        withContext(Dispatchers.IO) { multilink.sync(settings, tunnelRunning) }
            .onFailure { error ->
                AppLog.warn("Multilink", "Link merging settings were not applied: ${error.message}")
                if (error !is UnsupportedOperationException) onError(error)
            }
    }
    return rememberMultilinkCoreStatus(multilink, settings.enabled, tunnelRunning)
}

/** Core's view of the links, refreshed every second while merging is on and a tunnel runs. */
@Composable
fun rememberMultilinkCoreStatus(
    multilink: MultilinkSync,
    enabled: Boolean,
    tunnelRunning: Boolean,
): State<MultilinkStatus> {
    return produceState(MultilinkStatus(), multilink, enabled, tunnelRunning) {
        value = MultilinkStatus()
        if (!enabled || !tunnelRunning) return@produceState
        while (true) {
            value = withContext(Dispatchers.IO) { multilink.status() }
            delay(MultilinkStatusEveryMillis)
        }
    }
}
