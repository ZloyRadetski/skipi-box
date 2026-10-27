// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.SubscriptionRefreshResult
import app.skipi.app.subscription.commitSubscriptionRefresh
import app.skipi.app.subscription.loadSubscriptionRefresh
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.subscription.SubscriptionFetchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full refresh receipt, library-change flags, and selected-server transition at commit time. */
internal data class DesktopSubscriptionRefreshOperationResult(
    val refresh: SubscriptionRefreshResult,
    val serverLibraryChanged: Boolean,
    val profileLibraryChanged: Boolean,
    val previousSelectedServerId: Int? = null,
    val currentSelectedServerId: Int? = null,
)

/** Reuses shared loading/commit policy and the real Desktop latest-state persistence adapter. */
internal class DesktopSubscriptionRefreshOperation(
    private val subscriptionRepository: DesktopSubscriptionRepository,
    private val proxyServerRepository: DesktopProxyServerRepository,
    private val readConfigs: () -> DesktopConfigLibrary,
    private val saveConfigs: (DesktopConfigLibrary) -> Result<Unit>,
    private val publishConfigs: (DesktopConfigLibrary) -> Unit,
    private val readLatencyByServerId: () -> Map<Int, DesktopServerLatencyResult>,
    private val publishLatencyByServerId: (Map<Int, DesktopServerLatencyResult>) -> Unit,
    private val fetchResponse: suspend (String, String, DesktopSubscriptionRefreshFetchOptions) -> SubscriptionFetchResponse,
    private val parsers: List<ProxyServerPayloadParser>,
    private val refreshedAtMillis: () -> Long,
) {
    suspend fun run(
        request: SubscriptionRefreshLoadRequest<DesktopSubscriptionRefreshFetchOptions>,
    ): DesktopSubscriptionRefreshOperationResult {
        val commitAdapter = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = request.subscription.id,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyServerRepository,
            readConfigs = readConfigs,
            saveConfigs = saveConfigs,
            publishConfigs = publishConfigs,
            readLatencyByServerId = readLatencyByServerId,
            publishLatencyByServerId = publishLatencyByServerId,
        )
        val loaded = load(request)
        val committed = commitSubscriptionRefresh(
            loaded = loaded,
            refreshedAtMillis = refreshedAtMillis(),
            port = commitAdapter,
        )
        return DesktopSubscriptionRefreshOperationResult(
            refresh = SubscriptionRefreshResult(loaded = loaded, commit = committed),
            serverLibraryChanged = commitAdapter.serverLibraryChanged,
            profileLibraryChanged = commitAdapter.profileLibraryChanged,
            previousSelectedServerId = commitAdapter.previousSelectedServerId,
            currentSelectedServerId = commitAdapter.currentSelectedServerId,
        )
    }

    internal suspend fun load(
        request: SubscriptionRefreshLoadRequest<DesktopSubscriptionRefreshFetchOptions>,
    ) = withContext(Dispatchers.Default) {
        loadSubscriptionRefresh(
            request = request,
            fetchResponse = fetchResponse,
            parsers = parsers,
        )
    }
}
