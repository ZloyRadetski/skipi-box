// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.ProxyServerRecord as CatalogProxyServerRecord
import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.proxy.ProxyServerRecord as ReconciledProxyServerRecord
import app.skipi.app.proxy.reconcileConfigProxyGroups
import app.skipi.app.config.toTrafficConfigState
import features.subscription.DefaultSubscriptionGroupId

/** Bridges the latest Desktop config library into the shared generated-group catalogue operation. */
class DesktopTrafficConfigProxyGroupReconciler(
    private val serverRepository: DesktopProxyServerRepository,
    private val readConfigLibrary: () -> DesktopConfigLibrary,
    private val stripLeadingCountryFlag: (String) -> String,
) {
    /** The server repository owns latest-snapshot loading, persistence, and publish-after-save. */
    suspend fun reconcile() {
        serverRepository.updateCatalog { catalog ->
            val latestConfigLibrary = readConfigLibrary()
            val existingById = catalog.servers.associateBy(CatalogProxyServerRecord::id)
            val result = reconcileConfigProxyGroups(
                trafficConfigs = latestConfigLibrary.configs.map { profile -> profile.toTrafficConfigState() },
                activeTrafficConfigId = latestConfigLibrary.selectedConfigId ?: 0,
                servers = catalog.servers.map { server ->
                    ReconciledProxyServerRecord(
                        id = server.id,
                        groupId = server.sourceSubscriptionId ?: DefaultSubscriptionGroupId,
                        server = server.server,
                    )
                },
                nextServerId = catalog.nextServerId,
                selectedServerId = catalog.selectedServerId,
                autoBalancerGroupId = DefaultAutoBalancerGroupId,
                stripLeadingCountryFlag = stripLeadingCountryFlag,
                retainedServerIds = catalog.retainedServerIds,
            )
            catalog.copy(
                servers = result.servers.map { server ->
                    CatalogProxyServerRecord(
                        id = server.id,
                        server = server.server,
                        sourceSubscriptionId = server.groupId.takeIf { it != DefaultSubscriptionGroupId },
                        enabled = existingById[server.id]?.enabled ?: true,
                    )
                },
                nextServerId = result.nextServerId,
                selectedServerId = result.selectedServerId,
            )
        }
    }
}
