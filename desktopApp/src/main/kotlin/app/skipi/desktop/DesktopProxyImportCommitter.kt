// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

/**
 * Applies the non-server mutations from a previously reviewed [DesktopProxyImportPlan].
 *
 * The planner owns parsing and duplicate diagnostics. Server batches are counted
 * here but committed by the shared application store so IDs come from its latest
 * catalog snapshot rather than a detached library captured before import.
 */
object DesktopProxyImportCommitter {
    fun commit(
        plan: DesktopProxyImportPlan,
        subscriptionLibrary: DesktopSubscriptionLibrary,
        configLibrary: DesktopConfigLibrary,
        subscriptionUserAgent: String = DefaultDesktopSubscriptionUserAgent,
    ): DesktopProxyImportCommitResult {
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
                    addedServers += action.servers.size
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

private fun DesktopConfigLibrary.wouldReplace(action: DesktopProxyImportAction.AddConfig): Boolean {
    val sourceUrl = action.sourceUrl.trim().takeIf(String::isNotBlank)
    if (sourceUrl != null) {
        return configs.any { stored -> stored.sourceUrl.equals(sourceUrl, ignoreCase = true) }
    }
    val fallbackName = "Config ${(configs.maxOfOrNull(DesktopStoredConfig::id) ?: 0) + 1}"
    val name = action.name.trim().ifBlank { fallbackName }
    return configs.any { stored -> stored.name.equals(name, ignoreCase = true) }
}
