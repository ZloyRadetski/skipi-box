// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import features.logs.AppLog
import features.multilink.MultilinkSettings
import features.multilink.MultilinkStatus
import features.multilink.parseMultilinkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Keeps SKIPI Core's link merging in step with the settings and the tunnel: Core merges only while a tunnel runs.
 * Only a change reaches Core, so a running session is never disturbed by the same settings again. A Core library
 * without link merging is reported once through [unsupported] and then left alone.
 */
internal class DesktopMultilink(
    private val apply: (String) -> Result<Unit>,
    private val readStatus: () -> Result<String>,
) {
    private var applied: String? = null

    @Volatile
    var unsupported: Boolean = false
        private set

    @Synchronized
    fun sync(settings: MultilinkSettings, tunnelRunning: Boolean): Result<Unit> {
        if (unsupported) return Result.success(Unit)
        val json = settings.toCoreJson(tunnelRunning)
        if (json == applied) return Result.success(Unit)
        return apply(json)
            .onSuccess { applied = json }
            .onFailure { error -> if (error is UnsupportedOperationException) unsupported = true }
    }

    fun status(): MultilinkStatus {
        if (unsupported) return MultilinkStatus()
        return readStatus().map(::parseMultilinkStatus).getOrElse { error ->
            if (error is UnsupportedOperationException) unsupported = true
            MultilinkStatus()
        }
    }
}

/**
 * How long the tunnel may be down before Core stops merging: a session restart takes a moment, and stopping would
 * throw the measured link speeds away.
 */
internal const val DesktopMultilinkOffDelayMillis = 3_000L
internal const val DesktopMultilinkStatusEveryMillis = 1_000L

/**
 * Applies [settings] to Core while [tunnelRunning] and returns Core's view of the links, refreshed every second while
 * merging is on. A refused setting goes to [onError].
 */
@Composable
internal fun rememberDesktopMultilinkStatus(
    multilink: DesktopMultilink,
    settings: MultilinkSettings,
    tunnelRunning: Boolean,
    onError: (Throwable) -> Unit,
): State<MultilinkStatus> {
    LaunchedEffect(multilink, settings, tunnelRunning) {
        if (!tunnelRunning) delay(DesktopMultilinkOffDelayMillis)
        withContext(Dispatchers.IO) { multilink.sync(settings, tunnelRunning) }
            .onFailure { error ->
                AppLog.warn("Multilink", "Link merging settings were not applied: ${error.message}")
                if (error !is UnsupportedOperationException) onError(error)
            }
    }
    return produceState(MultilinkStatus(), multilink, settings.enabled, tunnelRunning) {
        value = MultilinkStatus()
        if (!settings.enabled || !tunnelRunning) return@produceState
        while (true) {
            value = withContext(Dispatchers.IO) { multilink.status() }
            delay(DesktopMultilinkStatusEveryMillis)
        }
    }
}
