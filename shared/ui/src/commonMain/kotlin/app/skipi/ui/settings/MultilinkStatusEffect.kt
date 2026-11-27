// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import features.logs.AppLog
import features.multilink.MultilinkController
import features.multilink.MultilinkSettings
import features.multilink.MultilinkStatus
import features.multilink.MultilinkStatusEveryMillis
import features.multilink.MultilinkSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext

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
    val controller = remember(multilink) {
        MultilinkController(
            scope = CoroutineScope(EmptyCoroutineContext),
            sync = multilink,
            settings = emptyFlow(),
            tunnelRunning = emptyFlow(),
            ioDispatcher = Dispatchers.IO,
        )
    }
    LaunchedEffect(controller, settings, tunnelRunning) {
        // The controller owns the grace period and the apply; this effect only hands it the current state.
        controller.applyOnce(settings, tunnelRunning)
            ?.onFailure { error ->
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
