// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout

/** Keeps scheduling responsive while each due pass remains a child of the app host. */
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun runDesktopResourceAutoUpdateLoop(
    updater: DesktopResourceAutoUpdater,
    wakeups: ReceiveChannel<Unit>,
    nowMillis: () -> Long,
    minimumDelayMillis: Long,
    idleDelayMillis: Long,
) = coroutineScope {
    require(minimumDelayMillis > 0L) { "Minimum resource-update delay must be positive" }
    require(idleDelayMillis >= minimumDelayMillis) { "Idle delay must not be shorter than the minimum delay" }

    fun CoroutineScope.launchDuePass() {
        launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                updater.runDue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                DesktopLogger.error("ResourceFiles", "Automatic resource update failed", error)
            }
        }
    }

    launchDuePass()
    while (isActive) {
        val waitMillis = updater.nextWakeupAtMillis()
            ?.let { nextWakeup -> (nextWakeup - nowMillis()).coerceIn(minimumDelayMillis, idleDelayMillis) }
            ?: idleDelayMillis

        select<Unit> {
            wakeups.onReceiveCatching { }
            onTimeout(waitMillis) { }
        }
        launchDuePass()
    }
}
