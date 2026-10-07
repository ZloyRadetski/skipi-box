// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.proxy.SubscriptionServerCollectionUpdate
import app.skipi.app.proxy.reconcileSubscriptionServerCollection
import features.config.decodeSkipiPayload
import features.proxy.server.model.ProxyServer
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.proxy.server.usecase.ProxyServerProviderUrlFetcher
import features.proxy.server.usecase.importer.importProxyServerPayloadText
import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionMetadata
import features.subscription.SubscriptionEmbeddedConfig
import features.subscription.subscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The exact subscription fields Android uses to decide whether a fetched response is still current. */
data class SubscriptionRefreshRequestIdentity(
    val id: Int,
    val url: String,
    val userAgent: String,
    val updateInterval: String,
    val ageSecretKey: String,
    val updateViaProxy: Boolean,
    val enabled: Boolean,
)

fun subscriptionRefreshRequestIdentity(subscription: SubscriptionRecord): SubscriptionRefreshRequestIdentity =
    SubscriptionRefreshRequestIdentity(
        id = subscription.id,
        url = subscription.url,
        userAgent = subscription.userAgent,
        updateInterval = subscription.updateInterval,
        ageSecretKey = subscription.ageSecretKey,
        updateViaProxy = subscription.updateViaProxy,
        enabled = subscription.enabled,
    )

/** Full current subscription snapshot plus opaque host fetch settings for one load. */
data class SubscriptionRefreshLoadRequest<FetchOptions>(
    val subscription: SubscriptionRecord,
    val fetchOptions: FetchOptions,
)

/** Profile content handed back to the host for its existing traffic-config integration. */
data class ResolvedEmbeddedSubscriptionConfig(
    val content: String,
    val sourceUrl: String,
    val fallbackName: String,
    val activate: Boolean,
)

/** Parsed response data. Hosts keep transport, Age decryption, and persistence at their own boundary. */
data class LoadedSubscriptionRefresh(
    val sourceIdentity: SubscriptionRefreshRequestIdentity,
    val urlCount: Int,
    val servers: List<ProxyServer<*>>,
    val metadata: SubscriptionMetadata,
    val resolvedEmbeddedConfig: ResolvedEmbeddedSubscriptionConfig?,
)

/** Aggregate state needed to apply a response with Android's latest-state semantics. */
data class SubscriptionRefreshSnapshot(
    val subscriptions: List<SubscriptionRecord>,
    val servers: List<ProxyServerRecord>,
    val nextServerId: Int,
    val selectedServerId: Int,
)

/** Result leaves a stale response's aggregate untouched and withholds its profile handoff. */
data class SubscriptionRefreshReconciliationResult(
    val applicable: Boolean,
    val snapshot: SubscriptionRefreshSnapshot,
    val resolvedEmbeddedConfig: ResolvedEmbeddedSubscriptionConfig?,
)

/**
 * Downloads and imports a subscription response using the host's transport callback for the
 * root response, Mihomo providers, and linked config URLs. The callback owns device headers,
 * timeout, proxy selection, HTTP behavior, and Age decryption; the common operation owns the
 * shared import order and Android embedded-config semantics.
 */
suspend fun <FetchOptions> loadSubscriptionRefresh(
    request: SubscriptionRefreshLoadRequest<FetchOptions>,
    fetchResponse: suspend (url: String, userAgent: String, options: FetchOptions) -> SubscriptionFetchResponse,
    parsers: List<ProxyServerPayloadParser>,
): LoadedSubscriptionRefresh {
    val subscription = request.subscription
    val rootResponse = fetchResponse(subscription.url, subscription.userAgent, request.fetchOptions)
    val context = ProxyServerImportContext(
        source = ProxyServerImportSource.SubscriptionUrl,
        providerUrlFetcher = ProxyServerProviderUrlFetcher { providerUrl ->
            fetchResponse(providerUrl, subscription.userAgent, request.fetchOptions).body
        },
    )
    val imported = importProxyServerPayloadText(
        text = rootResponse.body,
        context = context,
        parsers = parsers,
    )

    // Some format parsers fall back to inline data after catching a provider callback exception.
    // Ensure cancellation of the caller job is still observed before a successful load can escape.
    currentCoroutineContext().ensureActive()

    val metadata = rootResponse.subscriptionMetadata()
    val embeddedConfig = metadata.embeddedConfig?.let { embedded ->
        resolveEmbeddedSubscriptionConfig(
            subscriptionId = subscription.id,
            fallbackName = subscription.title,
            embedded = embedded,
            fetchResponse = { url ->
                fetchResponse(url, subscription.userAgent, request.fetchOptions)
            },
        )
    }

    require(imported.servers.isNotEmpty()) {
        "Subscription update imported no proxy servers"
    }
    currentCoroutineContext().ensureActive()

    return LoadedSubscriptionRefresh(
        sourceIdentity = subscriptionRefreshRequestIdentity(subscription),
        urlCount = imported.urlCount,
        servers = imported.servers,
        metadata = metadata,
        resolvedEmbeddedConfig = embeddedConfig,
    )
}

/** Reconciles against a host-supplied latest aggregate using the shared Android collection policy. */
fun reconcileSubscriptionRefresh(
    snapshot: SubscriptionRefreshSnapshot,
    loaded: LoadedSubscriptionRefresh,
    refreshedAtMillis: Long,
): SubscriptionRefreshReconciliationResult {
    val currentSubscription = snapshot.subscriptions.firstOrNull { subscription ->
        subscription.id == loaded.sourceIdentity.id &&
            subscriptionRefreshRequestIdentity(subscription) == loaded.sourceIdentity
    } ?: return SubscriptionRefreshReconciliationResult(
        applicable = false,
        snapshot = snapshot,
        resolvedEmbeddedConfig = null,
    )

    val collection = reconcileSubscriptionServerCollection(
        servers = snapshot.servers,
        updates = listOf(
            SubscriptionServerCollectionUpdate(
                groupId = currentSubscription.id,
                servers = loaded.servers,
                autoOverrideRules = currentSubscription.autoOverrideRules,
            ),
        ),
        nextServerId = snapshot.nextServerId,
        selectedServerId = snapshot.selectedServerId,
    )
    val refreshedSubscription = currentSubscription.withRefreshedMetadata(
        response = loaded.metadata,
        refreshedAtMillis = refreshedAtMillis,
    )
    val refreshedSnapshot = snapshot.copy(
        subscriptions = snapshot.subscriptions.map { subscription ->
            if (subscription.id == currentSubscription.id) refreshedSubscription else subscription
        },
        servers = collection.servers,
        nextServerId = collection.nextServerId,
        selectedServerId = collection.selectedServerId,
    )
    return SubscriptionRefreshReconciliationResult(
        applicable = true,
        snapshot = refreshedSnapshot,
        resolvedEmbeddedConfig = loaded.resolvedEmbeddedConfig,
    )
}

private suspend fun resolveEmbeddedSubscriptionConfig(
    subscriptionId: Int,
    fallbackName: String,
    embedded: SubscriptionEmbeddedConfig,
    fetchResponse: suspend (String) -> SubscriptionFetchResponse,
): ResolvedEmbeddedSubscriptionConfig? {
    val content = if (embedded.isUrl) {
        try {
            fetchResponse(embedded.payload).body
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    } else {
        embedded.payload.decodeSkipiPayload() ?: embedded.payload.trim()
    }
    return content?.takeIf(String::isNotBlank)?.let { resolved ->
        ResolvedEmbeddedSubscriptionConfig(
            content = resolved,
            sourceUrl = if (embedded.isUrl) embedded.payload.trim() else "subscription://$subscriptionId",
            fallbackName = fallbackName.ifBlank { "Subscription Config" },
            activate = embedded.activate,
        )
    }
}
