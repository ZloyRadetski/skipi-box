// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import android.net.Uri
import app.AppState
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.config.importTrafficConfigDocument
import features.proxy.server.model.ProxyServer

/** Android owns only conversion from [Uri] to the shared custom-link parser input. */
internal fun Uri.toSkipiDeepLinkOrNull(): SkipiDeepLink? = parseSkipiDeepLinkOrNull(toString())

/** Catalog transform used by the deep-link handler inside an atomic repository update. */
internal fun ProxyServerCatalog.withImportedSkipiServer(url: String): ProxyServerCatalog {
    val serverId = nextServerId
    return copy(
        servers = listOf(ProxyServerRecord(id = serverId, server = ProxyServer.parse(url))) + servers,
        nextServerId = if (serverId == Int.MAX_VALUE) Int.MAX_VALUE else serverId + 1,
        selectedServerId = serverId,
    )
}

internal fun AppState.withImportedTrafficConfig(
    content: String,
    activate: Boolean,
    fallbackName: String = "Config",
    sourceUrl: String = "",
): AppState {
    // Happ/Incy providers embed routing as a JSON payload (optionally base64).
    // Convert it to a regular profile so the normal import path can handle it.
    val converted = content.toRoscomRoutingJsonOrNull()
        ?.toRoscomRoutingShadowrocketConf(fallbackName)
    return withImportedTrafficConfigDocument(
        content = converted ?: content,
        activate = activate,
        fallbackName = fallbackName,
        sourceUrl = sourceUrl,
    )
}

private fun AppState.withImportedTrafficConfigDocument(
    content: String,
    activate: Boolean,
    fallbackName: String,
    sourceUrl: String,
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
