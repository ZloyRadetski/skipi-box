// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import features.subscription.SubscriptionSchedule
import features.subscription.parseSubscriptionSchedule

/**
 * Platform-neutral fields that determine profile and geo-resource refresh
 * schedules. Android and desktop-owned profile models adapt at their boundary.
 */
data class TrafficConfigRefreshTarget(
    val id: Int,
    val sourceUrl: String,
    val autoUpdate: Boolean,
    val updateLocked: Boolean,
    val updateInterval: String,
    val resourceAutoUpdate: Boolean,
    val resourceUpdateInterval: String,
)

enum class TrafficConfigExistingWorkPolicy {
    UPDATE,
}

data class TrafficConfigWorkSpec(
    val id: Int,
    val uniqueName: String,
    val repeatIntervalMillis: Long,
    val requiresConnectedNetwork: Boolean,
    val policy: TrafficConfigExistingWorkPolicy,
    val backoffMillis: Long,
    val isGeo: Boolean = false,
)

/** Platform gateway for persistent config and geo-resource refresh work. */
interface TrafficConfigScheduleGateway {
    fun scheduledConfigIds(): Set<Int>
    fun scheduledGeoConfigIds(): Set<Int>

    fun enqueueConfig(spec: TrafficConfigWorkSpec)
    fun enqueueGeo(spec: TrafficConfigWorkSpec)

    /** One-time geo refresh, e.g. right after a provider routing import. */
    fun enqueueGeoOnce(configId: Int)

    fun cancelConfig(configId: Int)
    fun cancelGeo(configId: Int)

    fun storeScheduledConfigIds(configIds: Set<Int>)
    fun storeScheduledGeoConfigIds(configIds: Set<Int>)
}

/** Reconciles persistent profile and geo-resource refresh work. */
class TrafficConfigScheduler(
    private val gateway: TrafficConfigScheduleGateway,
) {
    fun reconcile(targets: List<TrafficConfigRefreshTarget>) {
        val desiredConfigs = targets.mapNotNull { target ->
            val schedule = target.profileRefreshIntervalMillisOrNull() ?: return@mapNotNull null
            TrafficConfigWorkSpec(
                id = target.id,
                uniqueName = trafficConfigWorkName(target.id),
                repeatIntervalMillis = schedule,
                requiresConnectedNetwork = true,
                policy = TrafficConfigExistingWorkPolicy.UPDATE,
                backoffMillis = MinimumConfigBackoffMillis,
                isGeo = false,
            )
        }
        val desiredConfigIds = desiredConfigs.mapTo(mutableSetOf()) { it.id }
        (gateway.scheduledConfigIds() - desiredConfigIds).forEach(gateway::cancelConfig)
        desiredConfigs.forEach(gateway::enqueueConfig)
        gateway.storeScheduledConfigIds(desiredConfigIds)

        val desiredGeoConfigs = targets.mapNotNull { target ->
            val schedule = target.geoRefreshIntervalMillisOrNull() ?: return@mapNotNull null
            TrafficConfigWorkSpec(
                id = target.id,
                uniqueName = trafficConfigGeoWorkName(target.id),
                repeatIntervalMillis = schedule,
                requiresConnectedNetwork = true,
                policy = TrafficConfigExistingWorkPolicy.UPDATE,
                backoffMillis = MinimumConfigBackoffMillis,
                isGeo = true,
            )
        }
        val desiredGeoConfigIds = desiredGeoConfigs.mapTo(mutableSetOf()) { it.id }
        (gateway.scheduledGeoConfigIds() - desiredGeoConfigIds).forEach(gateway::cancelGeo)
        desiredGeoConfigs.forEach(gateway::enqueueGeo)
        gateway.storeScheduledGeoConfigIds(desiredGeoConfigIds)
    }
}

fun trafficConfigWorkName(configId: Int): String = "traffic-config-update-$configId"
fun trafficConfigGeoWorkName(configId: Int): String = "traffic-config-geo-update-$configId"
fun trafficConfigGeoOnceWorkName(configId: Int): String = "traffic-config-geo-once-$configId"

private fun TrafficConfigRefreshTarget.profileRefreshIntervalMillisOrNull(): Long? {
    if (!autoUpdate || updateLocked || sourceUrl.isBlank()) return null
    return (parseSubscriptionSchedule(updateInterval) as? SubscriptionSchedule.Enabled)
        ?.repeatIntervalMillis
}

private fun TrafficConfigRefreshTarget.geoRefreshIntervalMillisOrNull(): Long? {
    if (!resourceAutoUpdate) return null
    return (parseSubscriptionSchedule(resourceUpdateInterval) as? SubscriptionSchedule.Enabled)
        ?.repeatIntervalMillis
}

private const val MinimumConfigBackoffMillis = 15 * 60 * 1_000L
