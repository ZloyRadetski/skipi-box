// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.analyzeShadowrocketConfig
import features.subscription.SubscriptionMetadata
import features.proxy.server.model.ProxyServer

/**
 * Optimistic-concurrency helper for the part of a subscription refresh that
 * mutates local libraries.  Network I/O happens before this helper is called;
 * callers must pass the latest UI libraries, rather than the snapshots that
 * existed when the request was started.
 */
internal object DesktopSubscriptionRefreshCommitter {
    fun captureBaseline(
        subscriptions: DesktopSubscriptionLibrary,
        servers: DesktopServerLibrary,
        configs: DesktopConfigLibrary,
        url: String,
    ): DesktopSubscriptionRefreshBaseline {
        val normalizedUrl = url.trim()
        val subscription = subscriptions.subscriptions.firstOrNull { stored ->
            stored.url == normalizedUrl
        }
        return DesktopSubscriptionRefreshBaseline(
            subscription = subscription,
            subscriptionServers = subscription
                ?.let { stored -> DesktopServerLibraries.serversForSubscription(servers, stored.id) }
                .orEmpty(),
            configs = configs,
        )
    }

    /**
     * Rebase the imported proxy group onto the latest libraries.  A user edit
     * to the target subscription/group wins over an older network response;
     * unrelated manual servers and other subscription groups are retained.
     */
    fun rebaseServers(
        baseline: DesktopSubscriptionRefreshBaseline,
        latestSubscriptions: DesktopSubscriptionLibrary,
        latestServers: DesktopServerLibrary,
        url: String,
        userAgent: String,
        name: String,
        metadata: SubscriptionMetadata,
        importedServers: List<ProxyServer<*>>,
    ): DesktopSubscriptionRefreshServerCommit {
        val normalizedUrl = url.trim()
        val latestTarget = latestSubscriptions.subscriptions.firstOrNull { stored ->
            stored.url == normalizedUrl
        }
        if (latestTarget != baseline.subscription) {
            return DesktopSubscriptionRefreshServerCommit.Conflict(
                "Подписка была изменена во время загрузки.",
            )
        }

        val refreshedSubscriptions = DesktopSubscriptionLibraries.addOrReplace(
            library = latestSubscriptions,
            url = normalizedUrl,
            userAgent = userAgent,
            name = name,
            metadata = metadata,
        )
        val refreshedSubscription = refreshedSubscriptions.subscriptions.firstOrNull { stored ->
            stored.url == normalizedUrl
        } ?: return DesktopSubscriptionRefreshServerCommit.Conflict(
            "Не удалось определить группу серверов подписки.",
        )

        val latestTargetServers = DesktopServerLibraries.serversForSubscription(
            latestServers,
            refreshedSubscription.id,
        )
        val targetServerGroupChanged = baseline.subscription != null &&
            latestTargetServers != baseline.subscriptionServers
        val refreshedServers = if (targetServerGroupChanged) {
            latestServers
        } else {
            DesktopServerLibraries.replaceSubscriptionServers(
                library = latestServers,
                subscriptionId = refreshedSubscription.id,
                servers = importedServers,
            )
        }
        return DesktopSubscriptionRefreshServerCommit.Ready(
            subscriptions = refreshedSubscriptions,
            servers = refreshedServers,
            subscription = refreshedSubscription,
            serverGroupWasReplaced = !targetServerGroupChanged,
        )
    }

    /**
     * Imports a routing profile only when its target has not changed since the
     * refresh started.  This keeps a manually edited/created profile from
     * being overwritten by a delayed subscription response.
     */
    fun rebaseEmbeddedProfile(
        baseline: DesktopSubscriptionRefreshBaseline,
        latestConfigs: DesktopConfigLibrary,
        subscription: DesktopStoredSubscription,
        metadata: SubscriptionMetadata,
        resolved: DesktopResolvedEmbeddedConfig,
        nowMillis: Long,
    ): DesktopSubscriptionRefreshProfileCommit {
        val analysis = resolved.content.analyzeShadowrocketConfig()
        if (analysis.sections.isEmpty()) {
            return DesktopSubscriptionRefreshProfileCommit.Invalid(
                "профиль не похож на поддерживаемый .conf",
            )
        }
        val sourceUrl = resolved.sourceUrl.ifBlank { "subscription://${subscription.id}" }
        val baselineProfile = baseline.configs.configs.firstOrNull { config ->
            config.sourceUrl.equals(sourceUrl, ignoreCase = true)
        }
        val latestProfile = latestConfigs.configs.firstOrNull { config ->
            config.sourceUrl.equals(sourceUrl, ignoreCase = true)
        }
        if (latestProfile != baselineProfile) {
            return DesktopSubscriptionRefreshProfileCommit.Conflict
        }
        if (latestProfile?.updateLocked == true) {
            return DesktopSubscriptionRefreshProfileCommit.Locked
        }

        val fallbackName = metadata.profileTitle
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: subscription.name.ifBlank { "Subscription Config" }
        val profileContent = resolved.content.withDesktopSkipiProfileMetadata(
            name = fallbackName,
            sourceUrl = sourceUrl,
            updateLocked = false,
        )
        var updatedConfigs = DesktopConfigLibraries.put(
            library = latestConfigs,
            name = fallbackName,
            content = profileContent,
            sourceUrl = sourceUrl,
            updateLocked = false,
            lastUpdatedAtMillis = nowMillis,
        )
        val imported = updatedConfigs.configs.firstOrNull { config ->
            config.sourceUrl.equals(sourceUrl, ignoreCase = true)
        } ?: return DesktopSubscriptionRefreshProfileCommit.Invalid(
            "не удалось сохранить профиль",
        )
        if (resolved.activate && latestProfile == null) {
            updatedConfigs = DesktopConfigLibraries.select(updatedConfigs, imported.id)
        }
        return DesktopSubscriptionRefreshProfileCommit.Applied(
            configs = updatedConfigs,
            added = latestProfile == null,
        )
    }
}

internal data class DesktopSubscriptionRefreshBaseline(
    val subscription: DesktopStoredSubscription?,
    val subscriptionServers: List<DesktopStoredProxyServer>,
    val configs: DesktopConfigLibrary,
)

internal sealed interface DesktopSubscriptionRefreshServerCommit {
    data class Ready(
        val subscriptions: DesktopSubscriptionLibrary,
        val servers: DesktopServerLibrary,
        val subscription: DesktopStoredSubscription,
        val serverGroupWasReplaced: Boolean,
    ) : DesktopSubscriptionRefreshServerCommit

    data class Conflict(val message: String) : DesktopSubscriptionRefreshServerCommit
}

internal sealed interface DesktopSubscriptionRefreshProfileCommit {
    data class Applied(
        val configs: DesktopConfigLibrary,
        val added: Boolean,
    ) : DesktopSubscriptionRefreshProfileCommit

    data object Conflict : DesktopSubscriptionRefreshProfileCommit
    data object Locked : DesktopSubscriptionRefreshProfileCommit
    data class Invalid(val message: String) : DesktopSubscriptionRefreshProfileCommit
}
