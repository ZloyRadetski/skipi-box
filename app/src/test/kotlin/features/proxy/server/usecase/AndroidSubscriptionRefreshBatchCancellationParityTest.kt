// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import data.AndroidAppStateStore
import features.proxy.server.model.HTTP
import features.proxy.server.model.VLESS
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidSubscriptionRefreshBatchCancellationParityTest {
    @Test
    fun cancellationAfterCatalogWriteStillAppliesMetadataAndEmbeddedProfileWrite() = runBlocking {
        val stateStore = cancellationParityStateStore()
        val firstGroup = cancellationParitySubscription(id = 4, name = "Stored first")
        val secondGroup = cancellationParitySubscription(id = 5, name = "Stored second")
        stateStore.update(persist = false) { state ->
            state.copy(
                subscriptionGroups = listOf(firstGroup, secondGroup),
                proxyServers = listOf(
                    ProxyServerState(
                        id = 7,
                        server = HTTP(remarks = "Old first", server = "old-first.example.test", port = "443"),
                        groupId = firstGroup.id,
                    ),
                    ProxyServerState(
                        id = 8,
                        server = HTTP(remarks = "Old second", server = "old-second.example.test", port = "443"),
                        groupId = secondGroup.id,
                    ),
                ),
                nextProxyServerId = 9,
                selectedProxyServerId = 7,
                trafficConfigs = emptyList(),
                nextTrafficConfigId = 1,
                activeTrafficConfigId = 0,
            )
        }

        val callerJob = Job(coroutineContext[Job])
        var stateAtCatalogWrite: AppState? = null
        val cancelAtCatalogBoundary = launch(Dispatchers.Unconfined) {
            stateStore.state.first { state ->
                val catalogWasWritten = state.proxyServers.any { server ->
                    server.server.getInfo().remarks == "First catalog write"
                }
                if (catalogWasWritten) stateAtCatalogWrite = state
                catalogWasWritten
            }
            callerJob.cancel(CancellationException("caller cancelled after Android catalog write"))
        }

        val apply = async(callerJob) {
            applyProxySubscriptionUpdates(
                stateStore = stateStore,
                updates = listOf(
                    cancellationParityRefresh(
                        group = firstGroup,
                        remarks = "First catalog write",
                        title = "First metadata write",
                        configUrl = "subscription://4",
                        configContent = "[General]\ndns-server = 1.1.1.1\n[Rule]\nFINAL,DIRECT",
                    ),
                    cancellationParityRefresh(
                        group = secondGroup,
                        remarks = "Second catalog write",
                        title = "Second metadata write",
                        configUrl = "subscription://5",
                        configContent = "[General]\ndns-server = 8.8.8.8\n[Rule]\nFINAL,DIRECT",
                    ),
                ),
                updatedAtMillis = 1_400L,
                updateAppState = { transform -> stateStore.update(persist = false, transform = transform) },
            )
        }

        try {
            assertFailsWith<CancellationException> { apply.await() }

            val catalogBoundary = requireNotNull(stateAtCatalogWrite)
            assertEquals("Stored first", catalogBoundary.subscriptionGroups.first().name)
            assertEquals(emptyList(), catalogBoundary.trafficConfigs)

            val finalState = stateStore.currentState
            // The Android implementation at HEAD writes the repository catalog, then immediately
            // invokes updateAppState for subscription metadata and embedded profiles. There is no
            // cancellation check between those synchronous write calls.
            assertEquals(listOf("First metadata write", "Second metadata write"), finalState.subscriptionGroups.map { it.name })
            assertEquals(
                setOf("subscription://4", "subscription://5"),
                finalState.trafficConfigs.mapNotNull { it.sourceUrl }.toSet(),
            )
        } finally {
            cancelAtCatalogBoundary.cancelAndJoin()
            callerJob.cancel()
            stateStore.resetToStockState()
        }
    }
}

private suspend fun cancellationParityStateStore(): AndroidAppStateStore {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return AndroidAppStateStore.get(context).also { it.resetToStockState() }
}

private fun cancellationParitySubscription(id: Int, name: String) = SubscriptionGroupState(
    id = id,
    name = name,
    url = "https://subscription.example.test/$id",
    userAgent = "Skipi/1 Android",
    updateInterval = "6",
    ageSecretKey = "",
    updateViaProxy = false,
    autoOverrideRules = false,
    enabled = true,
)

private fun cancellationParityRefresh(
    group: SubscriptionGroupState,
    remarks: String,
    title: String,
    configUrl: String,
    configContent: String,
) = ProxyServerListSubscriptionUpdate(
    groupId = group.id,
    sourceIdentity = group.subscriptionFetchIdentity(),
    urlCount = 1,
    servers = listOf(
        VLESS(
            remarks = remarks,
            id = "uuid-$remarks",
            server = "${group.id}.new.example.test",
            port = "443",
        ),
    ),
    metadata = SubscriptionMetadata(profileTitle = title),
    resolvedConfig = ResolvedEmbeddedTrafficConfig(
        content = configContent,
        sourceUrl = configUrl,
        fallbackName = title,
        activate = true,
    ),
)
