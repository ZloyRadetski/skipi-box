// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigAutoUpdateExecutionResult
import features.config.runtime.TrafficConfigRefreshTarget
import features.config.runtime.TrafficConfigWorkSpec
import features.config.runtime.profileRefreshWorkSpecOrNull

/** Desktop owner of the periodic profile refresh jobs; any result but success is retried with backoff. */
internal class DesktopTrafficConfigAutoUpdater(
    readLatestTargets: () -> List<TrafficConfigRefreshTarget>,
    isNetworkConnected: () -> Boolean,
    updateAutomatically: suspend (Int) -> TrafficConfigAutoUpdateExecutionResult,
    nowMillis: () -> Long,
) {
    private val scheduler = DesktopPeriodicRefreshScheduler<TrafficConfigRefreshTarget, TrafficConfigWorkSpec>(
        specOf = TrafficConfigRefreshTarget::profileRefreshWorkSpecOrNull,
        idOf = TrafficConfigWorkSpec::id,
        readLatestTargets = readLatestTargets,
        isNetworkConnected = isNetworkConnected,
        cancelRunningWhenDisconnected = false,
        refresh = { profileId -> updateAutomatically(profileId) != TrafficConfigAutoUpdateExecutionResult.Success },
        nowMillis = nowMillis,
    )

    /** Creates, updates, or removes profile schedules while retaining existing work state. */
    fun observeTargets(targets: List<TrafficConfigRefreshTarget>) = scheduler.observeTargets(targets)

    /** Reconciles current eligibility and runs due connected profiles as structured children. */
    suspend fun runDue() = scheduler.runDue()

    /** The earliest profile deadline, if any profile remains eligible. */
    fun nextWakeupAtMillis(): Long? = scheduler.nextWakeupAtMillis()

    /** When the refresh loop should look again: no sooner than a minute while an item runs or the network is gone. */
    fun nextCheckAtMillis(): Long? = scheduler.nextCheckAtMillis()
}
