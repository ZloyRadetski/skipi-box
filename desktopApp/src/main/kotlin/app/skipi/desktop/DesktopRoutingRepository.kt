// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import app.skipi.app.repository.RoutingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Adapts the routing fields already persisted in Desktop settings.json. */
class DesktopRoutingRepository(
    initialSettings: DesktopAppSettings,
    private val readSettings: () -> DesktopAppSettings,
    private val saveSettings: (DesktopAppSettings) -> Result<Unit>,
    private val publishSettings: (DesktopAppSettings) -> Unit,
) : RoutingRepository {
    private val lock = Any()
    private val mutableState = MutableStateFlow(initialSettings.toRoutingConfigRecord())
    override val state: StateFlow<RoutingConfigRecord> = mutableState.asStateFlow()

    /** Refreshes the shared routing snapshot after host settings change elsewhere. */
    fun refresh() {
        synchronized(lock) {
            mutableState.value = readSettings().toRoutingConfigRecord()
        }
    }

    override suspend fun update(transform: (RoutingConfigRecord) -> RoutingConfigRecord) {
        synchronized(lock) {
            val current = readSettings()
            val updated = transform(current.toRoutingConfigRecord())
            val next = current.copy(
                routeDomainStrategy = updated.domainStrategy,
                defaultRouteOutboundTag = updated.defaultOutboundTag,
                routeRules = updated.rules,
                nextRouteRuleId = maxOf(
                    current.nextRouteRuleId,
                    (updated.rules.maxOfOrNull { it.id } ?: 0) + 1,
                ),
            )

            saveSettings(next).getOrThrow()
            publishSettings(next)
            mutableState.value = next.toRoutingConfigRecord()
        }
    }
}

internal fun DesktopAppSettings.toRoutingConfigRecord() = RoutingConfigRecord(
    domainStrategy = routeDomainStrategy,
    defaultOutboundTag = defaultRouteOutboundTag,
    rules = routeRules,
)
