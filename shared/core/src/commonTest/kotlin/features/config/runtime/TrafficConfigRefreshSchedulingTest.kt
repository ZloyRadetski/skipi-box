// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficConfigRefreshSchedulingTest {
    @Test
    fun reconciles_profile_and_geo_work_independently() {
        val gateway = RecordingGateway(
            scheduledConfigIds = setOf(1, 9),
            scheduledGeoConfigIds = setOf(2, 8),
        )
        TrafficConfigScheduler(gateway).reconcile(
            listOf(
                target(
                    id = 1,
                    sourceUrl = "https://example.test/profile",
                    autoUpdate = true,
                    updateInterval = "6",
                    resourceAutoUpdate = true,
                    resourceUpdateInterval = "24",
                ),
                target(
                    id = 2,
                    sourceUrl = "https://example.test/locked",
                    autoUpdate = true,
                    updateLocked = true,
                    updateInterval = "6",
                    resourceAutoUpdate = true,
                    resourceUpdateInterval = "12",
                ),
                target(
                    id = 3,
                    sourceUrl = "",
                    autoUpdate = true,
                    updateInterval = "6",
                    resourceAutoUpdate = false,
                    resourceUpdateInterval = "24",
                ),
                target(
                    id = 4,
                    sourceUrl = "https://example.test/invalid",
                    autoUpdate = true,
                    updateInterval = "0.24",
                    resourceAutoUpdate = true,
                    resourceUpdateInterval = "invalid",
                ),
            ),
        )

        assertEquals(listOf(9), gateway.cancelledConfigIds)
        assertEquals(listOf(8), gateway.cancelledGeoIds)
        assertEquals(
            listOf(
                spec(id = 1, uniqueName = "traffic-config-update-1", hours = 6, isGeo = false),
            ),
            gateway.enqueuedConfigSpecs,
        )
        assertEquals(
            listOf(
                spec(id = 1, uniqueName = "traffic-config-geo-update-1", hours = 24, isGeo = true),
                spec(id = 2, uniqueName = "traffic-config-geo-update-2", hours = 12, isGeo = true),
            ),
            gateway.enqueuedGeoSpecs,
        )
        assertEquals(setOf(1), gateway.storedConfigIds)
        assertEquals(setOf(1, 2), gateway.storedGeoIds)
    }

    @Test
    fun produces_stable_work_names_for_each_work_kind() {
        assertEquals("traffic-config-update-7", trafficConfigWorkName(7))
        assertEquals("traffic-config-geo-update-7", trafficConfigGeoWorkName(7))
        assertEquals("traffic-config-geo-once-7", trafficConfigGeoOnceWorkName(7))
    }

    private fun target(
        id: Int,
        sourceUrl: String,
        autoUpdate: Boolean,
        updateLocked: Boolean = false,
        updateInterval: String,
        resourceAutoUpdate: Boolean,
        resourceUpdateInterval: String,
    ) = TrafficConfigRefreshTarget(
        id = id,
        sourceUrl = sourceUrl,
        autoUpdate = autoUpdate,
        updateLocked = updateLocked,
        updateInterval = updateInterval,
        resourceAutoUpdate = resourceAutoUpdate,
        resourceUpdateInterval = resourceUpdateInterval,
    )

    private fun spec(
        id: Int,
        uniqueName: String,
        hours: Int,
        isGeo: Boolean,
    ) = TrafficConfigWorkSpec(
        id = id,
        uniqueName = uniqueName,
        repeatIntervalMillis = hours * HourMillis,
        requiresConnectedNetwork = true,
        policy = TrafficConfigExistingWorkPolicy.UPDATE,
        backoffMillis = 15 * MinuteMillis,
        isGeo = isGeo,
    )

    private class RecordingGateway(
        private val scheduledConfigIds: Set<Int>,
        private val scheduledGeoConfigIds: Set<Int>,
    ) : TrafficConfigScheduleGateway {
        val cancelledConfigIds = mutableListOf<Int>()
        val cancelledGeoIds = mutableListOf<Int>()
        val enqueuedConfigSpecs = mutableListOf<TrafficConfigWorkSpec>()
        val enqueuedGeoSpecs = mutableListOf<TrafficConfigWorkSpec>()
        var storedConfigIds: Set<Int>? = null
        var storedGeoIds: Set<Int>? = null

        override fun scheduledConfigIds(): Set<Int> = scheduledConfigIds
        override fun scheduledGeoConfigIds(): Set<Int> = scheduledGeoConfigIds
        override fun enqueueConfig(spec: TrafficConfigWorkSpec) {
            enqueuedConfigSpecs += spec
        }

        override fun enqueueGeo(spec: TrafficConfigWorkSpec) {
            enqueuedGeoSpecs += spec
        }

        override fun enqueueGeoOnce(configId: Int) = Unit

        override fun cancelConfig(configId: Int) {
            cancelledConfigIds += configId
        }

        override fun cancelGeo(configId: Int) {
            cancelledGeoIds += configId
        }

        override fun storeScheduledConfigIds(configIds: Set<Int>) {
            storedConfigIds = configIds
        }

        override fun storeScheduledGeoConfigIds(configIds: Set<Int>) {
            storedGeoIds = configIds
        }
    }

    private companion object {
        const val MinuteMillis = 60_000L
        const val HourMillis = 60 * MinuteMillis
    }
}
