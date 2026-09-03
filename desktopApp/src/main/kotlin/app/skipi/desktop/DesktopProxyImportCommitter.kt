// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.encodePersistedProxyServer

/**
 * Applies a previously reviewed [DesktopProxyImportPlan] entirely in memory.
 *
 * The planner owns parsing and duplicate diagnostics; this layer owns the
 * Android-compatible local mutations.  In particular, direct links always
 * become manual servers and never acquire a subscription id.  Persistence is
 * deliberately left to the caller so the three libraries can be saved as one
 * UI operation.
 */
object DesktopProxyImportCommitter {
    fun commit(
        plan: DesktopProxyImportPlan,
        serverLibrary: DesktopServerLibrary,
        subscriptionLibrary: DesktopSubscriptionLibrary,
        configLibrary: DesktopConfigLibrary,
        subscriptionUserAgent: String = DefaultDesktopSubscriptionUserAgent,
    ): DesktopProxyImportCommitResult {
        var nextServers = serverLibrary
        var nextSubscriptions = subscriptionLibrary
        var nextConfigs = configLibrary
        var addedServers = 0
        var addedSubscriptions = 0
        var updatedSubscriptions = 0
        var addedConfigs = 0
        var updatedConfigs = 0

        plan.actions.forEach { action ->
            when (action) {
                is DesktopProxyImportAction.AddServers -> {
                    val imported = action.servers.toManualStoredServers(nextServers)
                    if (imported.isNotEmpty()) {
                        // Android prepends imported servers and retains the existing
                        // choice whenever it still points to a real server.
                        val mergedServers = imported + nextServers.servers
                        nextServers = nextServers.copy(
                            selectedServerId = nextServers.selectedServerId
                                ?.takeIf { selectedId ->
                                    mergedServers.any { stored -> stored.id == selectedId }
                                }
                                ?: mergedServers.firstOrNull()?.id,
                            servers = mergedServers,
                        )
                        addedServers += imported.size
                    }
                }

                is DesktopProxyImportAction.AddSubscription -> {
                    val normalizedUrl = action.url.trim()
                    val replacesExisting = nextSubscriptions.subscriptions.any { stored ->
                        stored.url == normalizedUrl
                    }
                    nextSubscriptions = DesktopSubscriptionLibraries.addOrReplace(
                        library = nextSubscriptions,
                        url = action.url,
                        userAgent = subscriptionUserAgent,
                    )
                    if (replacesExisting) updatedSubscriptions += 1 else addedSubscriptions += 1
                }

                is DesktopProxyImportAction.AddConfig -> {
                    val replacesExisting = nextConfigs.wouldReplace(action)
                    nextConfigs = DesktopConfigLibraries.put(
                        library = nextConfigs,
                        name = action.name,
                        content = action.content,
                        sourceUrl = action.sourceUrl,
                        updateLocked = action.updateLocked,
                    )
                    if (replacesExisting) updatedConfigs += 1 else addedConfigs += 1
                }
            }
        }

        return DesktopProxyImportCommitResult(
            serverLibrary = nextServers,
            subscriptionLibrary = nextSubscriptions,
            configLibrary = nextConfigs,
            counts = DesktopProxyImportCommitCounts(
                addedServers = addedServers,
                addedSubscriptions = addedSubscriptions,
                addedConfigs = addedConfigs,
                updatedSubscriptions = updatedSubscriptions,
                updatedConfigs = updatedConfigs,
            ),
        )
    }
}

/** Detailed counters make the import confirmation usable without re-inspecting its libraries. */
data class DesktopProxyImportCommitCounts(
    val addedServers: Int = 0,
    val addedSubscriptions: Int = 0,
    val addedConfigs: Int = 0,
    val updatedSubscriptions: Int = 0,
    val updatedConfigs: Int = 0,
) {
    val addedCount: Int
        get() = addedServers + addedSubscriptions + addedConfigs

    val updatedCount: Int
        get() = updatedSubscriptions + updatedConfigs

    val summary: String
        get() {
            val added = listOfNotNull(
                addedServers.takeIf { it > 0 }?.let { "серверов: $it" },
                addedSubscriptions.takeIf { it > 0 }?.let { "подписок: $it" },
                addedConfigs.takeIf { it > 0 }?.let { "конфигов: $it" },
            )
            val updated = listOfNotNull(
                updatedSubscriptions.takeIf { it > 0 }?.let { "подписок: $it" },
                updatedConfigs.takeIf { it > 0 }?.let { "конфигов: $it" },
            )
            return when {
                added.isEmpty() && updated.isEmpty() -> "Новых элементов для импорта нет."
                updated.isEmpty() -> "Добавлено: ${added.joinToString()}."
                added.isEmpty() -> "Обновлено: ${updated.joinToString()}."
                else -> "Добавлено: ${added.joinToString()}; обновлено: ${updated.joinToString()}."
            }
        }
}

data class DesktopProxyImportCommitResult(
    val serverLibrary: DesktopServerLibrary,
    val subscriptionLibrary: DesktopSubscriptionLibrary,
    val configLibrary: DesktopConfigLibrary,
    val counts: DesktopProxyImportCommitCounts,
) {
    val addedCount: Int
        get() = counts.addedCount

    val updatedCount: Int
        get() = counts.updatedCount

    /** Short user-facing text suitable for the desktop snackbar. */
    val summary: String
        get() = counts.summary
}

private fun List<ProxyServer<*>>.toManualStoredServers(
    library: DesktopServerLibrary,
): List<DesktopStoredProxyServer> {
    var nextId = library.servers.maxOfOrNull(DesktopStoredProxyServer::id) ?: 0
    return map { server ->
        check(nextId < Int.MAX_VALUE) { "No desktop server IDs remain" }
        DesktopStoredProxyServer(
            id = ++nextId,
            serverJson = server.encodePersistedProxyServer(),
            subscriptionId = null,
        )
    }
}

private fun DesktopConfigLibrary.wouldReplace(action: DesktopProxyImportAction.AddConfig): Boolean {
    val sourceUrl = action.sourceUrl.trim().takeIf(String::isNotBlank)
    if (sourceUrl != null) {
        return configs.any { stored -> stored.sourceUrl.equals(sourceUrl, ignoreCase = true) }
    }
    val fallbackName = "Config ${(configs.maxOfOrNull(DesktopStoredConfig::id) ?: 0) + 1}"
    val name = action.name.trim().ifBlank { fallbackName }
    return configs.any { stored -> stored.name.equals(name, ignoreCase = true) }
}
