// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.usecase

import app.AppState
import app.SubscriptionGroupState
import features.logs.AndroidAppLogger
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerListSubscriptionFailure
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdate
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdateResult
import features.proxy.server.usecase.importProxyServersFromText
import features.proxy.server.usecase.subscriptionFetchIdentity
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import features.subscription.runtime.AndroidSubscriptionFetcher
import features.subscription.runtime.SubscriptionFetchResponse
import features.subscription.subscriptionMetadata
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
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
): ProxyServerListSubscriptionUpdateResult = withContext(Dispatchers.Default) {
    supervisorScope {
        val results = groups.map { group ->
            async {
                group to coordinator.withGroup(group.id) {
                    updateSubscriptionGroup(
                        group = group,
                        fetchResponse = fetchResponse,
                        fetchOptions = fetchOptions(group),
                    )
                }
            }
        }.awaitAll()
        val updates = results
            .mapNotNull { (_, result) -> result.getOrNull() }
        val failures = results.mapNotNull { (group, result) ->
            result.exceptionOrNull()?.let { error ->
                ProxyServerListSubscriptionFailure(groupId = group.id, error = error)
            }
        }
        ProxyServerListSubscriptionUpdateResult(
            updates = updates,
            failures = failures,
            updatedAtMillis = Clock.System.now().toEpochMilliseconds(),
        )
    }
}

private suspend fun updateSubscriptionGroup(
    group: SubscriptionGroupState,
    fetchResponse: suspend (String, String, AndroidSubscriptionFetchOptions) -> SubscriptionFetchResponse,
    fetchOptions: AndroidSubscriptionFetchOptions,
): Result<ProxyServerListSubscriptionUpdate> {
    return runCatching {
        val response = fetchResponse(group.url, group.userAgent, fetchOptions)
        val text = response.body
        val importResult = importProxyServersFromText(
            text = text,
            source = ProxyServerImportSource.SubscriptionUrl,
            providerUrlFetcher = { providerUrl ->
                fetchResponse(providerUrl, group.userAgent, fetchOptions).body
            },
        )
        ProxyServerListSubscriptionUpdate(
            groupId = group.id,
            sourceIdentity = group.subscriptionFetchIdentity(),
            urlCount = importResult.urlCount,
            servers = importResult.servers,
            metadata = response.subscriptionMetadata(),
        ).also { update ->
            if (update.servers.isEmpty()) {
                AndroidAppLogger.warn(
                    LogTag,
                    "Subscription update imported no proxy servers ${group.logIdentity()} " +
                        "parsedProxyServerCount=${update.urlCount} responseLength=${text.length}",
                )
            }
            require(update.servers.isNotEmpty()) {
                "Subscription update imported no proxy servers"
            }
        }
    }.onFailure { error ->
        AndroidAppLogger.warn(
            LogTag,
            "Subscription update failed ${group.logIdentity()}",
            error,
        )
    }
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
