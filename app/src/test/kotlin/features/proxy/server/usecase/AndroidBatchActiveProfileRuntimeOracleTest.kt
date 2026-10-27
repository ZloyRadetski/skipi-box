// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ProxyServerState
import app.SubscriptionGroupState
import data.AndroidAppStateStore
import features.config.TrafficConfigState
import features.proxy.server.list.observeHomeServerSelection
import features.proxy.server.model.VLESS
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Android source oracle: Home reconnects on selected proxy ID changes, not active traffic-profile changes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AndroidBatchActiveProfileRuntimeOracleTest {
    @Test
    fun batchActivatingEmbeddedProfileDoesNotRestartWhenSelectedProxyServerStaysTheSame() = runBlocking {
        val stateStore = cleanStateStore()
        val firstGroup = subscriptionGroup(id = 4)
        val secondGroup = subscriptionGroup(id = 5)
        val selectedProxyId = 33
        val originalActiveProfileId = 6
        val originalActiveProfile = TrafficConfigState(
            id = originalActiveProfileId,
            name = "Manual profile",
            rawConfig = "[Rule]\nFINAL,DIRECT",
        )
        stateStore.update(persist = false) { state ->
            state.copy(
                subscriptionGroups = listOf(firstGroup, secondGroup),
                proxyServers = listOf(
                    ProxyServerState(
                        id = 7,
                        server = VLESS(
                            remarks = "Old subscription server",
                            id = "subscription-server",
                            server = "old.example.test",
                            port = "443",
                        ),
                        groupId = firstGroup.id,
                    ),
                    ProxyServerState(
                        id = selectedProxyId,
                        server = VLESS(
                            remarks = "Selected manual server",
                            id = "manual-server",
                            server = "manual.example.test",
                            port = "443",
                        ),
                        groupId = 90,
                    ),
                ),
                nextProxyServerId = 34,
                selectedProxyServerId = selectedProxyId,
                proxyRunning = true,
                trafficConfigs = listOf(originalActiveProfile),
                nextTrafficConfigId = originalActiveProfileId + 1,
                activeTrafficConfigId = originalActiveProfileId,
            )
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val restartRequests = mutableListOf<Int>()
        val observer = scope.observeHomeServerSelection(stateStore.state, restartRequests::add)
        try {
            applyProxySubscriptionUpdates(
                stateStore = stateStore,
                updates = listOf(
                    refreshUpdate(
                        group = firstGroup,
                        servers = listOf(
                            VLESS(
                                remarks = "Refreshed subscription server",
                                id = "subscription-server",
                                server = "new.example.test",
                                port = "443",
                            ),
                        ),
                        resolvedConfig = ResolvedEmbeddedTrafficConfig(
                            content = "[Rule]\nFINAL,PROXY",
                            sourceUrl = "subscription://${firstGroup.id}",
                            fallbackName = "Imported active profile",
                            activate = true,
                        ),
                    ),
                    refreshUpdate(group = secondGroup, servers = emptyList()),
                ),
                updatedAtMillis = 1_400L,
                updateAppState = { transform -> stateStore.update(persist = false, transform = transform) },
            )

            val refreshed = stateStore.currentState
            assertEquals(selectedProxyId, refreshed.selectedProxyServerId)
            assertEquals(originalActiveProfileId + 1, refreshed.activeTrafficConfigId)
            assertNotEquals(originalActiveProfile, refreshed.trafficConfigs.single { it.id == refreshed.activeTrafficConfigId })
            assertTrue(refreshed.proxyRunning)
            assertEquals(emptyList(), restartRequests)
        } finally {
            observer.cancel()
            scope.cancel()
            stateStore.resetToStockState()
        }
    }

    private suspend fun cleanStateStore(): AndroidAppStateStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AndroidAppStateStore.get(context).also { it.resetToStockState() }
    }

    private fun subscriptionGroup(id: Int) = SubscriptionGroupState(
        id = id,
        name = "Stored subscription $id",
        url = "https://subscription.example.test/$id",
        userAgent = "Skipi/1 Android",
        updateInterval = "6",
        ageSecretKey = "",
        updateViaProxy = false,
        autoOverrideRules = false,
        enabled = true,
    )

    private fun refreshUpdate(
        group: SubscriptionGroupState,
        servers: List<VLESS>,
        resolvedConfig: ResolvedEmbeddedTrafficConfig? = null,
    ) = ProxyServerListSubscriptionUpdate(
        groupId = group.id,
        sourceIdentity = group.subscriptionFetchIdentity(),
        urlCount = servers.size,
        servers = servers,
        metadata = SubscriptionMetadata(profileTitle = "Updated ${group.id}"),
        resolvedConfig = resolvedConfig,
    )
}
