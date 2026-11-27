// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.config.importTrafficConfigDocument
import features.proxy.server.model.ProxyServer

/** Catalog transform used by the deep-link handler inside an atomic repository update. */
fun ProxyServerCatalog.withImportedSkipiServer(url: String): ProxyServerCatalog {
    val serverId = nextServerId
    return copy(
        servers = listOf(ProxyServerRecord(id = serverId, server = ProxyServer.parse(url))) + servers,
        nextServerId = if (serverId == Int.MAX_VALUE) Int.MAX_VALUE else serverId + 1,
        selectedServerId = serverId,
    )
}

fun AppState.withImportedTrafficConfig(
    content: String,
    activate: Boolean,
    fallbackName: String = "Config",
    sourceUrl: String = "",
): AppState {
    val result = importTrafficConfigDocument(
        trafficConfigs = trafficConfigs,
        nextTrafficConfigId = nextTrafficConfigId,
        activeTrafficConfigId = activeTrafficConfigId,
        content = content,
        activate = activate,
        fallbackName = fallbackName,
        sourceUrl = sourceUrl,
        newProfileResourceSettings = androidDefaultTrafficConfigResourceSettings(),
    )
    // Existing profiles are refreshed without activation; onadd applies only to a new profile.
    if (result.trafficConfigs == trafficConfigs) return this
    return copy(
        trafficConfigs = result.trafficConfigs,
        nextTrafficConfigId = result.nextTrafficConfigId,
        activeTrafficConfigId = result.activeTrafficConfigId,
    )
}
