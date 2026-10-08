// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.usecase

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.SubscriptionRefreshSnapshot
import app.skipi.app.subscription.loadSubscriptionRefresh
import app.skipi.app.subscription.reconcileSubscriptionRefresh
import features.proxy.server.model.Socks
import features.proxy.server.model.VLESS
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.proxy.server.usecase.importer.parseProxyServersFromPayloads
import features.proxy.server.usecase.withUpdatedSubscriptionServers
import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionMetadata
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidSubscriptionRefreshParityTest {
    @Test
    fun androidLoadAndStateApplyMatchTheSharedSingleRefreshOperation() = runTest {
        val group = subscriptionGroup()
        val fetchOptions = AndroidSubscriptionFetchOptions(
            useRunningProxy = true,
            ageSecretKey = group.ageSecretKey,
            sendDeviceHeaders = false,
            timeoutSeconds = 17,
        )
        val responseBodies = mapOf(
            RootUrl to rootSubscriptionBody,
            ProviderUrl to providerBody,
            EmbeddedConfigUrl to "[General]\nmode = rule",
        )
        val androidRequests = mutableListOf<FetchCall>()

        val androidResult = updateSubscriptions(
            groups = listOf(group),
            fetchOptions = { fetchOptions },
            fetchText = { url, userAgent, options ->
                androidRequests += FetchCall(url, userAgent, options)
                assertEquals(fetchOptions, options)
                responseBodies.getValue(url)
            },
        )

        assertEquals(1, androidResult.updatedGroupCount)
        assertEquals(0, androidResult.failedGroupCount)
        val androidUpdate = androidResult.updates.single()
        val androidParser: ProxyServerPayloadParser = { payload, context ->
            parseProxyServersFromPayloads(listOf(payload), context)
        }
        val commonRequests = mutableListOf<FetchCall>()
        val commonLoaded = loadSubscriptionRefresh(
            request = SubscriptionRefreshLoadRequest(
                subscription = group.toSubscriptionRecord(),
                fetchOptions = fetchOptions,
            ),
            fetchResponse = { url, userAgent, options ->
                commonRequests += FetchCall(url, userAgent, options)
                assertEquals(fetchOptions, options)
                SubscriptionFetchResponse(body = responseBodies.getValue(url))
            },
            parsers = listOf(androidParser),
        )

        assertEquals(androidRequests, commonRequests)
        assertEquals(listOf(RootUrl, ProviderUrl, EmbeddedConfigUrl), androidRequests.map(FetchCall::url))
        assertEquals(group.userAgent, androidRequests.first().userAgent)
        assertTrue(androidRequests.all { it.options == fetchOptions })
        assertEquals(androidUpdate.urlCount, commonLoaded.urlCount)
        assertEquals(androidUpdate.servers, commonLoaded.servers)
        assertEquals(androidUpdate.metadata, commonLoaded.metadata)
        assertEquals("Updated provider profile", androidUpdate.metadata.profileTitle)
        assertEquals("18", androidUpdate.metadata.profileUpdateIntervalHours)
        assertEquals(
            "[General]\nmode = rule",
            androidUpdate.resolvedConfig?.content,
        )
        val initialState = appState(group, commonLoaded.servers)

        val snapshot = initialState.toRefreshSnapshot()
        val expected = reconcileSubscriptionRefresh(
            snapshot = snapshot,
            loaded = commonLoaded,
            refreshedAtMillis = androidResult.updatedAtMillis,
        )
        assertTrue(expected.applicable)

        val applied = initialState.withUpdatedSubscriptionServers(
            updates = androidResult.updates,
            updatedAtMillis = androidResult.updatedAtMillis,
        )

        assertEquals(
            expected.snapshot.servers,
            applied.proxyServers.map { server ->
                ProxyServerRecord(server.id, server.groupId, server.server, server.latency)
            },
        )
        assertEquals(expected.snapshot.nextServerId, applied.nextProxyServerId)
        assertEquals(expected.snapshot.selectedServerId, applied.selectedProxyServerId)
        assertEquals("Updated provider profile", applied.subscriptionGroups.single().name)
        assertEquals("18", applied.subscriptionGroups.single().updateInterval)
        assertEquals(androidResult.updatedAtMillis, applied.subscriptionGroups.single().lastUpdatedAtMillis)
        assertEquals("https://support.example.test/help", applied.subscriptionGroups.single().supportUrl)

        val appliedRemote = assertIs<Socks>(
            applied.proxyServers.single { it.server.getInfo().remarks == "Remote provider node" }.server,
        )
        assertEquals("remote.example.test", appliedRemote.server)
        assertEquals("31 ms", applied.proxyServers.single { it.id == 10 }.latency)
        assertFalse(applied.subscriptionGroups.single().autoOverrideRules)
    }
}

private data class FetchCall(
    val url: String,
    val userAgent: String,
    val options: AndroidSubscriptionFetchOptions,
)

private fun subscriptionGroup() = SubscriptionGroupState(
    id = 4,
    name = "Stored group name",
    url = RootUrl,
    userAgent = "Skipi/1 Android",
    updateInterval = "6",
    hwid = "device-4",
    ageSecretKey = "age-secret",
    updateViaProxy = true,
    autoOverrideRules = false,
    enabled = true,
    builtIn = false,
    lastUpdatedAtMillis = 100L,
    profileTitle = "Stored profile title",
    announce = "Stored announcement",
    supportUrl = "https://support.example.test/old",
    supportEmail = "old@example.test",
    profileWebPageUrl = "https://provider.example.test",
    announceUrl = "https://provider.example.test/old-announcement",
    trafficUploadBytes = 20L,
    trafficDownloadBytes = 30L,
    trafficTotalBytes = 200L,
    trafficExpireAtSeconds = 1_900L,
)

private fun appState(
    group: SubscriptionGroupState,
    loadedServers: List<features.proxy.server.model.ProxyServer<*>>,
): AppState {
    val matchingVless = assertIs<VLESS>(loadedServers.first { it is VLESS })
    return AppState(
        subscriptionGroups = listOf(group),
        proxyServers = listOf(
            ProxyServerState(
                id = 10,
                server = matchingVless.copy(remarks = "Previous root node"),
                groupId = group.id,
                latency = "31 ms",
            ),
            ProxyServerState(
                id = 11,
                server = Socks(remarks = "Manual server", server = "manual.example.test", port = "1080"),
                groupId = 99,
                latency = "9 ms",
            ),
        ),
        nextProxyServerId = 20,
        selectedProxyServerId = 10,
    )
}

private fun SubscriptionGroupState.toSubscriptionRecord() = SubscriptionRecord(
    id = id,
    title = name,
    url = url,
    userAgent = userAgent,
    updateInterval = updateInterval,
    hwid = hwid,
    ageSecretKey = ageSecretKey,
    updateViaProxy = updateViaProxy,
    autoOverrideRules = autoOverrideRules,
    enabled = enabled,
    builtIn = builtIn,
    metadata = SubscriptionMetadata(
        profileTitle = profileTitle,
        announce = announce,
        supportUrl = supportUrl,
        supportEmail = supportEmail,
        profileWebPageUrl = profileWebPageUrl,
        announceUrl = announceUrl,
        trafficUploadBytes = trafficUploadBytes,
        trafficDownloadBytes = trafficDownloadBytes,
        trafficTotalBytes = trafficTotalBytes,
        trafficExpireAtSeconds = trafficExpireAtSeconds,
        profileUpdateIntervalHours = updateInterval,
    ),
    lastUpdatedAtMillis = lastUpdatedAtMillis,
    notifyOnExpiry = notifyOnExpiry,
    customExpiryReminders = customExpiryReminders,
)

private fun AppState.toRefreshSnapshot() = SubscriptionRefreshSnapshot(
    subscriptions = subscriptionGroups.map(SubscriptionGroupState::toSubscriptionRecord),
    servers = proxyServers.map { server ->
        ProxyServerRecord(server.id, server.groupId, server.server, server.latency)
    },
    nextServerId = nextProxyServerId,
    selectedServerId = selectedProxyServerId,
)

private val rootSubscriptionBody = """
    #profile-title: Updated provider profile
    #support-url: https://support.example.test/help
    #profile-update-interval: 18
    #autorouting: $EmbeddedConfigUrl
    proxies:
      - name: Root VLESS node
        type: vless
        server: root.example.test
        port: 443
        uuid: 11111111-1111-4111-8111-111111111111
        tls: true
    proxy-providers:
      remote:
        type: http
        url: $ProviderUrl
        payload:
          - name: Inline provider node
            type: socks
            server: inline.example.test
            port: 1080
""".trimIndent()

private val providerBody = """
    proxies:
      - name: Remote provider node
        type: socks
        server: remote.example.test
        port: 1080
""".trimIndent()

private const val RootUrl = "https://subscription.example.test/root.yaml"
private const val ProviderUrl = "https://provider.example.test/nodes.yaml"
private const val EmbeddedConfigUrl = "https://config.example.test/profile.conf"
