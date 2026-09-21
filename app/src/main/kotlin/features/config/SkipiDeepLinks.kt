// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import android.net.Uri
import app.AppState
import app.ProxyServerState
import features.proxy.server.model.ProxyServer
import features.subscription.DefaultSubscriptionGroupId

/** Android owns only conversion from [Uri] to the shared custom-link parser input. */
internal fun Uri.toSkipiDeepLinkOrNull(): SkipiDeepLink? = parseSkipiDeepLinkOrNull(toString())

internal fun AppState.withImportedSkipiServer(url: String): AppState {
    val server = ProxyServer.parse(url)
    val serverId = nextProxyServerId
    val nextServers = listOf(
        ProxyServerState(
            id = serverId,
            server = server,
            groupId = DefaultSubscriptionGroupId,
        ),
    ) + proxyServers
    return copy(
        proxyServers = nextServers,
        nextProxyServerId = serverId + 1,
        selectedProxyServerId = serverId,
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
    val normalized = content.trimEnd() + "\n"
    require(normalized.isNotBlank()) { "Configuration is empty" }
    val analysis = normalized.analyzeShadowrocketConfig()
    require(analysis.diagnostics.none { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) {
        analysis.diagnostics.first { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }.message
    }
    val configName = normalized.lineSequence()
        .firstOrNull { line -> line.trim().startsWith("#") && line.contains("name", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf(String::isNotBlank)

    val cleanSourceUrl = sourceUrl.trim()
    val existing = trafficConfigs.firstOrNull { config ->
        when {
            cleanSourceUrl.isNotBlank() && config.sourceUrl.isNotBlank() && config.sourceUrl.trim().equals(cleanSourceUrl, ignoreCase = true) -> true
            configName != null && config.name.trim().equals(configName, ignoreCase = true) -> true
            fallbackName.isNotBlank() && fallbackName != "Config" && config.name.trim().equals(fallbackName.trim(), ignoreCase = true) -> true
            else -> false
        }
    }
    if (existing != null) {
        val effectiveSourceUrl = if (cleanSourceUrl.isNotBlank()) cleanSourceUrl else existing.sourceUrl
        val updated = existing.copy(
            rawConfig = normalized,
            name = configName ?: existing.name,
            sourceUrl = effectiveSourceUrl,
        ).withSkipiSettingsReadFromRawConfig().let { parsed ->
            parsed.copy(
                sourceUrl = effectiveSourceUrl.ifBlank { parsed.sourceUrl },
                name = configName ?: existing.name,
            ).withSkipiSettingsInRawConfig()
        }
        // An already-known config is only refreshed: activation ("onadd")
        // happens exclusively when the config appears for the first time.
        // Receiving it again (e.g., a subscription refresh) must update the
        // content without re-enabling it.
        return withUpdatedTrafficConfig(existing.id) { updated }
    }
    val configId = nextTrafficConfigId
    val name = configName ?: if (fallbackName.isNotBlank() && fallbackName != "Config") fallbackName else "$fallbackName $configId"
    val imported = TrafficConfigState(
        id = configId,
        name = name,
        sourceUrl = cleanSourceUrl,
        rawConfig = normalized,
    ).withSkipiSettingsReadFromRawConfig().let { parsed ->
        parsed.copy(
            sourceUrl = cleanSourceUrl.ifBlank { parsed.sourceUrl },
        ).withSkipiSettingsInRawConfig()
    }
    return copy(
        trafficConfigs = trafficConfigs + imported,
        nextTrafficConfigId = configId + 1,
        activeTrafficConfigId = if (activate) configId else activeTrafficConfigId,
    ).withConfigProxyGroupsReflected()
}
