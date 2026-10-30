// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.skipi.app.config.toTrafficConfigState
import app.skipi.app.model.SubscriptionRecord
import features.config.TrafficConfigState
import features.config.runtime.TrafficConfigRefreshTarget
import features.subscription.runtime.SubscriptionRefreshTarget
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
internal suspend fun runDesktopAutomaticRefreshLoop(
    runDue: suspend () -> Unit,
    nextWakeupAtMillis: () -> Long?,
    wakeups: ReceiveChannel<Unit>,
    nowMillis: () -> Long,
    minimumDelayMillis: Long,
    idleDelayMillis: Long,
    onFailure: (Throwable) -> Unit,
) = coroutineScope {
    require(minimumDelayMillis > 0L) { "Minimum automatic-refresh delay must be positive" }
    require(idleDelayMillis >= minimumDelayMillis) { "Idle delay must not be shorter than the minimum delay" }

    fun CoroutineScope.launchDuePass() {
        launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                runDue()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                onFailure(error)
            }
        }
    }

    launchDuePass()
    while (isActive) {
        val waitMillis = nextWakeupAtMillis()
            ?.let { nextWakeup -> (nextWakeup - nowMillis()).coerceIn(minimumDelayMillis, idleDelayMillis) }
            ?: idleDelayMillis

        select<Unit> {
            wakeups.onReceiveCatching { }
            onTimeout(waitMillis) { }
        }
        launchDuePass()
    }
}

internal const val DesktopSubscriptionSchedulerMinimumDelayMillis = 1_000L
internal const val DesktopSubscriptionSchedulerIdleDelayMillis = 60L * 60L * 1_000L

/** Runs [runDue] on its schedule while [enabled]; the loop restarts when [updater] or [enabled] changes. */
@Composable
internal fun DesktopAutomaticRefresh(
    updater: Any,
    enabled: Boolean = true,
    failureSource: String,
    failureMessage: String,
    runDue: suspend () -> Unit,
    nextWakeupAtMillis: () -> Long?,
    wakeups: ReceiveChannel<Unit>,
) {
    LaunchedEffect(updater, enabled) {
        if (!enabled) return@LaunchedEffect
        runDesktopAutomaticRefreshLoop(
            runDue = runDue,
            nextWakeupAtMillis = nextWakeupAtMillis,
            wakeups = wakeups,
            nowMillis = System::currentTimeMillis,
            minimumDelayMillis = DesktopSubscriptionSchedulerMinimumDelayMillis,
            idleDelayMillis = DesktopSubscriptionSchedulerIdleDelayMillis,
            onFailure = { error -> DesktopLogger.error(failureSource, failureMessage, error) },
        )
    }
}

internal fun SubscriptionRecord.toRefreshTarget() = SubscriptionRefreshTarget(
    id = id,
    url = url,
    enabled = enabled,
    updateInterval = updateInterval,
    lastUpdatedAtMillis = lastUpdatedAtMillis ?: 0L,
)

internal fun TrafficConfigState.toDesktopTrafficConfigRefreshTarget() = TrafficConfigRefreshTarget(
    id = id,
    sourceUrl = sourceUrl,
    autoUpdate = autoUpdate,
    updateLocked = updateLocked,
    updateInterval = updateInterval,
    resourceAutoUpdate = resourceSettings.autoUpdate,
    resourceUpdateInterval = resourceSettings.updateInterval,
)

/** Profiles as traffic-config states, using Desktop's resource user-agent fallback. */
internal fun DesktopConfigLibrary.toTrafficConfigStates(): List<TrafficConfigState> =
    configs.map { profile -> profile.toTrafficConfigState(desktopResourceProfileFallbackUserAgent()) }

internal fun DesktopConfigLibrary.toRefreshTargets(): List<TrafficConfigRefreshTarget> =
    toTrafficConfigStates().map(TrafficConfigState::toDesktopTrafficConfigRefreshTarget)
