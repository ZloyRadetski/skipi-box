// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import features.config.TrafficConfigState
import features.subscription.SubscriptionSchedule
import features.subscription.parseSubscriptionSchedule

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

interface TrafficConfigScheduleGateway {
    fun scheduledConfigIds(): Set<Int>
    fun scheduledGeoConfigIds(): Set<Int>

    fun enqueueConfig(spec: TrafficConfigWorkSpec)
    fun enqueueGeo(spec: TrafficConfigWorkSpec)

    fun cancelConfig(configId: Int)
    fun cancelGeo(configId: Int)

    fun storeScheduledConfigIds(configIds: Set<Int>)
    fun storeScheduledGeoConfigIds(configIds: Set<Int>)
}

class TrafficConfigScheduler(
    private val gateway: TrafficConfigScheduleGateway,
) {
    fun reconcile(configs: List<TrafficConfigState>) {
        // 1. Reconcile config auto-updates
        val desiredConfigs = configs.mapNotNull { config ->
            val schedule = parseSubscriptionSchedule(config.updateInterval)
            if (!config.autoUpdate || config.updateLocked || config.sourceUrl.isBlank() || schedule !is SubscriptionSchedule.Enabled) {
                null
            } else {
                TrafficConfigWorkSpec(
                    id = config.id,
                    uniqueName = trafficConfigWorkName(config.id),
                    repeatIntervalMillis = schedule.repeatIntervalMillis,
                    requiresConnectedNetwork = true,
                    policy = TrafficConfigExistingWorkPolicy.UPDATE,
                    backoffMillis = MinimumConfigBackoffMillis,
                    isGeo = false,
                )
            }
        }
        val desiredConfigIds = desiredConfigs.mapTo(mutableSetOf()) { it.id }
        (gateway.scheduledConfigIds() - desiredConfigIds).forEach(gateway::cancelConfig)
        desiredConfigs.forEach(gateway::enqueueConfig)
        gateway.storeScheduledConfigIds(desiredConfigIds)

        // 2. Reconcile geo auto-updates
        val desiredGeoConfigs = configs.mapNotNull { config ->
            val schedule = parseSubscriptionSchedule(config.resourceSettings.updateInterval)
            if (!config.resourceSettings.autoUpdate || schedule !is SubscriptionSchedule.Enabled) {
                null
            } else {
                TrafficConfigWorkSpec(
                    id = config.id,
                    uniqueName = trafficConfigGeoWorkName(config.id),
                    repeatIntervalMillis = schedule.repeatIntervalMillis,
                    requiresConnectedNetwork = true,
                    policy = TrafficConfigExistingWorkPolicy.UPDATE,
                    backoffMillis = MinimumConfigBackoffMillis,
                    isGeo = true,
                )
            }
        }
        val desiredGeoConfigIds = desiredGeoConfigs.mapTo(mutableSetOf()) { it.id }
        (gateway.scheduledGeoConfigIds() - desiredGeoConfigIds).forEach(gateway::cancelGeo)
        desiredGeoConfigs.forEach(gateway::enqueueGeo)
        gateway.storeScheduledGeoConfigIds(desiredGeoConfigIds)
    }
}

fun trafficConfigWorkName(configId: Int): String = "traffic-config-update-$configId"
fun trafficConfigGeoWorkName(configId: Int): String = "traffic-config-geo-update-$configId"

private const val MinimumConfigBackoffMillis = 15 * 60 * 1_000L
