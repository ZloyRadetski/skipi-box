// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.usecase

import app.AppState
import app.SubscriptionGroupState
import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.SubscriptionRefreshLoader
import app.skipi.app.subscription.SubscriptionRefreshBatchOperation
import app.skipi.app.subscription.SubscriptionRefreshBatchProgress
import app.skipi.app.subscription.loadSubscriptionRefresh
import app.skipi.app.subscription.refreshSubscriptions
import data.repository.toSubscriptionRecord
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServerListSubscriptionFailure
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdate
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdateResult
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.proxy.server.usecase.importer.parseProxyServersFromPayloads
import features.proxy.server.usecase.subscriptionFetchIdentity
import features.subscription.SubscriptionFetchResponse
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import features.subscription.runtime.AndroidSubscriptionFetcher
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ui.text.formatTemplate
import kotlin.time.Clock

private const val LogTag = "SubscriptionUpdateUseCase"

internal suspend fun updateSubscriptions(
    groups: List<SubscriptionGroupState>,
    subscriptionFetcher: AndroidSubscriptionFetcher,
    fetchOptions: (SubscriptionGroupState) -> AndroidSubscriptionFetchOptions,
): ProxyServerListSubscriptionUpdateResult = updateSubscriptionsFromResponses(
    groups = groups,
    fetchOptions = fetchOptions,
    fetchResponse = { url, userAgent, options ->
        subscriptionFetcher.fetchResponse(url, userAgent, options)
    },
)

internal suspend fun updateSubscriptions(
    groups: List<SubscriptionGroupState>,
    fetchOptions: (SubscriptionGroupState) -> AndroidSubscriptionFetchOptions,
    fetchText: suspend (String, String, AndroidSubscriptionFetchOptions) -> String,
    coordinator: SubscriptionUpdateCoordinator = DefaultSubscriptionUpdateCoordinator,
): ProxyServerListSubscriptionUpdateResult = updateSubscriptionsFromResponses(
        groups = groups,
        fetchOptions = fetchOptions,
        fetchResponse = { url, userAgent, options ->
            SubscriptionFetchResponse(body = fetchText(url, userAgent, options))
        },
        coordinator = coordinator,
    )

private suspend fun updateSubscriptionsFromResponses(
    groups: List<SubscriptionGroupState>,
    fetchOptions: (SubscriptionGroupState) -> AndroidSubscriptionFetchOptions,
    fetchResponse: suspend (String, String, AndroidSubscriptionFetchOptions) -> SubscriptionFetchResponse,
    coordinator: SubscriptionUpdateCoordinator = DefaultSubscriptionUpdateCoordinator,
): ProxyServerListSubscriptionUpdateResult {
    val groupsById = groups.associateBy(SubscriptionGroupState::id)
    val requests = groups.map { group ->
        SubscriptionRefreshLoadRequest(
            subscription = group.toSubscriptionRecord(),
            // Compute current Android headers/proxy options only when this request enters
            // its per-group lock, after any preceding refresh of the same subscription.
            fetchOptions = { fetchOptions(group) },
        )
    }
    val batch = refreshSubscriptions(
        requests = requests,
        loader = SubscriptionRefreshLoader { request ->
            val group = checkNotNull(groupsById[request.subscription.id])
            coordinator.withGroup(group.id) {
                withContext(Dispatchers.Default) {
                    updateSubscriptionGroup(
                        group = group,
                        fetchResponse = fetchResponse,
                        fetchOptions = request.fetchOptions(),
                    ).getOrThrow()
                }
            }
        },
        updatedAtMillis = { Clock.System.now().toEpochMilliseconds() },
    )
    return ProxyServerListSubscriptionUpdateResult(
        updates = batch.updates,
        failures = batch.failures.map { failure ->
            ProxyServerListSubscriptionFailure(failure.request.subscription.id, failure.error)
        },
        updatedAtMillis = batch.updatedAtMillis,
    )
}

/** Retained Android bulk operation. Loading and the host aggregate commit share one re-entry gate. */
internal class AndroidSubscriptionRefreshBatchRunner(
    private val captureGroups: () -> List<SubscriptionGroupState>,
    private val onBeforeEligibility: () -> Unit,
    private val fetchOptions: (SubscriptionGroupState) -> AndroidSubscriptionFetchOptions,
    private val fetchResponse: suspend (String, String, AndroidSubscriptionFetchOptions) -> SubscriptionFetchResponse,
    private val onCommitUpdates: suspend (List<ProxyServerListSubscriptionUpdate>, Long) -> Unit,
    private val coordinator: SubscriptionUpdateCoordinator = DefaultSubscriptionUpdateCoordinator,
    private val refreshedAtMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val operation = SubscriptionRefreshBatchOperation(
        captureRequests = {
            onBeforeEligibility()
            captureGroups().map { group ->
                SubscriptionRefreshLoadRequest(
                    subscription = group.toSubscriptionRecord(),
                    // Read current host transport options only after acquiring the per-ID lock.
                    fetchOptions = AndroidSubscriptionRefreshBatchLoadContext(
                        group = group,
                        readFetchOptions = { fetchOptions(group) },
                    ),
                )
            }
        },
        loader = SubscriptionRefreshLoader { request -> loadBatchUpdate(request) },
        refreshedAtMillis = refreshedAtMillis,
        commitSingle = { update, updatedAtMillis ->
            onCommitUpdates(listOf(update), updatedAtMillis)
        },
        commitBatch = { updates, updatedAtMillis ->
            onCommitUpdates(updates, updatedAtMillis)
        },
    )

    val progress: StateFlow<SubscriptionRefreshBatchProgress> = operation.progress

    suspend fun run(): ProxyServerListSubscriptionUpdateResult {
        val result = operation.run().refresh
        return ProxyServerListSubscriptionUpdateResult(
            updates = result.updates,
            failures = result.failures.map { failure ->
                ProxyServerListSubscriptionFailure(failure.request.subscription.id, failure.error)
            },
            updatedAtMillis = result.updatedAtMillis,
        )
    }

    private suspend fun loadBatchUpdate(
        request: SubscriptionRefreshLoadRequest<AndroidSubscriptionRefreshBatchLoadContext>,
    ): ProxyServerListSubscriptionUpdate {
        val context = request.fetchOptions
        return try {
            coordinator.withGroup(context.group.id) {
                withContext(Dispatchers.Default) {
                    val loaded = loadSubscriptionRefresh(
                        request = SubscriptionRefreshLoadRequest(
                            subscription = request.subscription,
                            fetchOptions = context.readFetchOptions(),
                        ),
                        fetchResponse = fetchResponse,
                        parsers = listOf(AndroidSubscriptionRefreshPayloadParser),
                    )
                    loaded.toProxyServerListSubscriptionUpdate(context.group)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AndroidAppLogger.warn(
                LogTag,
                "Subscription update failed ${context.group.logIdentity()}",
                error,
            )
            throw error
        }
    }
}

private data class AndroidSubscriptionRefreshBatchLoadContext(
    val group: SubscriptionGroupState,
    val readFetchOptions: () -> AndroidSubscriptionFetchOptions,
)

private fun LoadedSubscriptionRefresh.toProxyServerListSubscriptionUpdate(
    group: SubscriptionGroupState,
): ProxyServerListSubscriptionUpdate = ProxyServerListSubscriptionUpdate(
    groupId = group.id,
    sourceIdentity = group.subscriptionFetchIdentity(),
    urlCount = urlCount,
    servers = servers,
    metadata = metadata,
    resolvedConfig = resolvedEmbeddedConfig?.let { config ->
        features.proxy.server.usecase.ResolvedEmbeddedTrafficConfig(
            content = config.content,
            sourceUrl = config.sourceUrl,
            fallbackName = config.fallbackName,
            activate = config.activate,
        )
    },
)

private suspend fun updateSubscriptionGroup(
    group: SubscriptionGroupState,
    fetchResponse: suspend (String, String, AndroidSubscriptionFetchOptions) -> SubscriptionFetchResponse,
    fetchOptions: AndroidSubscriptionFetchOptions,
): Result<ProxyServerListSubscriptionUpdate> {
    return try {
        val loaded = loadSubscriptionRefresh(
            request = SubscriptionRefreshLoadRequest(
                subscription = group.toSubscriptionRecord(),
                fetchOptions = fetchOptions,
            ),
            fetchResponse = fetchResponse,
            parsers = listOf(AndroidSubscriptionRefreshPayloadParser),
        )
        val update = ProxyServerListSubscriptionUpdate(
            groupId = group.id,
            sourceIdentity = group.subscriptionFetchIdentity(),
            urlCount = loaded.urlCount,
            servers = loaded.servers,
            metadata = loaded.metadata,
            resolvedConfig = loaded.resolvedEmbeddedConfig?.let { config ->
                features.proxy.server.usecase.ResolvedEmbeddedTrafficConfig(
                    content = config.content,
                    sourceUrl = config.sourceUrl,
                    fallbackName = config.fallbackName,
                    activate = config.activate,
                )
            },
        )
        Result.success(update)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        AndroidAppLogger.warn(
            LogTag,
            "Subscription update failed ${group.logIdentity()}",
            error,
        )
        Result.failure(error)
    }
}

private val AndroidSubscriptionRefreshPayloadParser: ProxyServerPayloadParser = { payload, context ->
    parseProxyServersFromPayloads(listOf(payload), context)
}

internal class SubscriptionUpdateCoordinator {
    private val mutexes = ConcurrentHashMap<Int, Mutex>()

    suspend fun <T> withGroup(groupId: Int, block: suspend () -> T): T {
        return mutexes.computeIfAbsent(groupId) { Mutex() }.withLock { block() }
    }
}

private val DefaultSubscriptionUpdateCoordinator = SubscriptionUpdateCoordinator()

internal fun AppState.toSubscriptionFetchOptions(
    sendDeviceHeaders: Boolean = enableSubscriptionDeviceHeaders,
    useRunningProxy: Boolean = false,
    ageSecretKey: String = "",
    timeoutSeconds: Int = subscriptionFetchTimeoutSeconds,
): AndroidSubscriptionFetchOptions {
    return AndroidSubscriptionFetchOptions(
        useRunningProxy = useRunningProxy,
        ageSecretKey = ageSecretKey,
        sendDeviceHeaders = sendDeviceHeaders,
        timeoutSeconds = timeoutSeconds,
    )
}

internal fun AppState.toSubscriptionFetchOptions(group: SubscriptionGroupState): AndroidSubscriptionFetchOptions {
    return AndroidSubscriptionFetchOptions(
        useRunningProxy = group.updateViaProxy && proxyRunning,
        ageSecretKey = group.ageSecretKey,
        sendDeviceHeaders = enableSubscriptionDeviceHeaders,
        timeoutSeconds = subscriptionFetchTimeoutSeconds,
    )
}

internal fun subscriptionUpdateMessage(
    result: ProxyServerListSubscriptionUpdateResult,
    successTemplate: String,
    failedTemplate: String,
): String {
    if (result.failedGroupCount > 0 && result.updatedGroupCount == 0) {
        val firstFailureMessage = result.failures.firstOrNull()?.error?.let(::formatSubscriptionErrorMessage)
        if (!firstFailureMessage.isNullOrBlank()) {
            return firstFailureMessage
        }
    } else if (result.failedGroupCount > 0) {
        val firstFailureMessage = result.failures.firstOrNull()?.error?.let(::formatSubscriptionErrorMessage)
        val base = failedTemplate.formatTemplate(
            "groupCount" to result.updatedGroupCount,
            "failedCount" to result.failedGroupCount,
            "serverCount" to result.importedServerCount,
        )
        return if (!firstFailureMessage.isNullOrBlank()) "$base: $firstFailureMessage" else base
    }
    return successTemplate.formatTemplate(
        "groupCount" to result.updatedGroupCount,
        "failedCount" to result.failedGroupCount,
        "serverCount" to result.importedServerCount,
    )
}

internal fun formatSubscriptionErrorMessage(error: Throwable): String {
    val unwrapped = generateSequence(error) { it.cause }.lastOrNull() ?: error
    return when {
        error is features.subscription.SubscriptionHttpException -> error.message.orEmpty()
        unwrapped is features.subscription.SubscriptionHttpException -> unwrapped.message.orEmpty()
        unwrapped is java.net.UnknownHostException -> "DNS error: ${unwrapped.message ?: "Unknown host"}"
        unwrapped is java.net.SocketTimeoutException -> "Timeout connecting to server"
        unwrapped is java.net.ConnectException -> "Connection failed: ${unwrapped.message ?: "Refused"}"
        unwrapped is java.net.SocketException -> "Network error: ${unwrapped.message ?: "Socket exception"}"
        error.message?.contains("imported no proxy servers", ignoreCase = true) == true -> "Subscription returned empty list"
        !error.message.isNullOrBlank() -> error.message!!
        else -> unwrapped.localizedMessage ?: "Unknown error"
    }
}

private fun SubscriptionGroupState.logIdentity(): String {
    return "groupId=$id groupName=${name.ifBlank { "<blank>" }} " +
        "urlHost=${url.toLogHost()} userAgent=${userAgent.ifBlank { "<blank>" }}"
}

private fun String.toLogHost(): String {
    return runCatching { URI(this).host }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
        ?: "<unknown>"
}
