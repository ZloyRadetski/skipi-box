// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import android.net.Uri
import app.AppState
import app.ProxyServerState
import features.proxy.server.model.ProxyServer
import features.subscription.DefaultSubscriptionGroupId
import utils.decodeFlexibleBase64OrNull

internal sealed interface SkipiDeepLink {
    data class Subscription(val url: String) : SkipiDeepLink
    data class ManualServer(val url: String) : SkipiDeepLink
    data class TrafficConfig(
        val content: String,
        val activate: Boolean,
        val sourceUrl: String = "",
    ) : SkipiDeepLink
    data object Connect : SkipiDeepLink
    data object Open : SkipiDeepLink
    data object Disconnect : SkipiDeepLink
    data object Close : SkipiDeepLink
    data object Toggle : SkipiDeepLink
}

/** Parses only SKIPI's documented links; unrelated custom URI schemes are ignored. */
internal fun Uri.toSkipiDeepLinkOrNull(): SkipiDeepLink? {
    if (!scheme.equals("skipi", ignoreCase = true)) return null
    return when (host?.lowercase()) {
        "add" -> rawPayloadAfter("skipi://add/")?.toSubscriptionOrServerLink()
        "import" -> rawPayloadAfter("skipi://import/")?.toSubscriptionOrServerLink()
        "routing", "conf" -> {
            val raw = toString()
            val addPrefix = if (raw.startsWith("skipi://routing/add/", ignoreCase = true)) "skipi://routing/add/" else "skipi://conf/add/"
            val onAddPrefix = if (raw.startsWith("skipi://routing/onadd/", ignoreCase = true)) "skipi://routing/onadd/" else "skipi://conf/onadd/"
            when {
                raw.startsWith("skipi://routing/add/", ignoreCase = true) || raw.startsWith("skipi://conf/add/", ignoreCase = true) ->
                    rawPayloadAfter(addPrefix)?.let { payload ->
                        val decoded = payload.decodeSkipiPayload() ?: payload.trim()
                        val isUrl = decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)
                        SkipiDeepLink.TrafficConfig(
                            content = decoded,
                            activate = false,
                            sourceUrl = if (isUrl) decoded else "",
                        )
                    }

                raw.startsWith("skipi://routing/onadd/", ignoreCase = true) || raw.startsWith("skipi://conf/onadd/", ignoreCase = true) ->
                    rawPayloadAfter(onAddPrefix)?.let { payload ->
                        val decoded = payload.decodeSkipiPayload() ?: payload.trim()
                        val isUrl = decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)
                        SkipiDeepLink.TrafficConfig(
                            content = decoded,
                            activate = true,
                            sourceUrl = if (isUrl) decoded else "",
                        )
                    }

                else -> null
            }
        }

        "connect" -> SkipiDeepLink.Connect
        "open" -> SkipiDeepLink.Open
        "disconnect" -> SkipiDeepLink.Disconnect
        "close" -> SkipiDeepLink.Close
        "toggle" -> SkipiDeepLink.Toggle
        else -> null
    }
}

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
        return withUpdatedTrafficConfig(existing.id) { updated }.let { state ->
            if (activate) state.copy(activeTrafficConfigId = existing.id) else state
        }
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

private fun Uri.rawPayloadAfter(prefix: String): String? {
    return toString()
        .takeIf { value -> value.startsWith(prefix, ignoreCase = true) }
        ?.substring(prefix.length)
        ?.takeIf(String::isNotBlank)
        ?.let(Uri::decode)
}

private fun String.toSubscriptionOrServerLink(): SkipiDeepLink? {
    val decoded = decodeSkipiPayload() ?: trim()
    return when {
        decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true) ->
            SkipiDeepLink.Subscription(decoded)

        decoded.contains("://") -> SkipiDeepLink.ManualServer(decoded)
        else -> null
    }
}

internal fun String.decodeSkipiPayload(): String? {
    return trim().decodeFlexibleBase64OrNull()
        ?.decodeToString()
        ?.trim()
        ?.takeIf(String::isNotBlank)
}
