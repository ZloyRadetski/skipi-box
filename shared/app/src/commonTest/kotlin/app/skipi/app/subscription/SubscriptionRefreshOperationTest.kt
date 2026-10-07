// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.ProxyServerRecord
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.proxy.server.usecase.EmptyProxyServerImportResult
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.proxy.server.usecase.importer.importProxyServersFromProviderPayload
import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SubscriptionRefreshOperationTest {
    @Test
    fun omittedResponseIntervalKeepsCurrentDirectValueWhileRetainingStoredMetadata() {
        val current = subscription().copy(
            updateInterval = "12",
            metadata = SubscriptionMetadata(profileUpdateIntervalHours = "6"),
        )

        val withoutServerInterval = current.withRefreshedMetadata(
            response = SubscriptionMetadata(),
            refreshedAtMillis = 100L,
        )
        val withServerInterval = current.withRefreshedMetadata(
            response = SubscriptionMetadata(profileUpdateIntervalHours = "24"),
            refreshedAtMillis = 200L,
        )

        assertEquals("6", withoutServerInterval.metadata?.profileUpdateIntervalHours)
        assertEquals("12", withoutServerInterval.updateInterval)
        assertEquals("24", withServerInterval.metadata?.profileUpdateIntervalHours)
        assertEquals("24", withServerInterval.updateInterval)
    }

    @Test
    fun requestIdentityUsesOnlyTheAndroidFreshnessFields() {
        val original = subscription()
        val identity = subscriptionRefreshRequestIdentity(original)

        assertEquals(
            identity,
            subscriptionRefreshRequestIdentity(
                original.copy(
                    title = "Renamed while fetching",
                    autoOverrideRules = false,
                    hwid = "changed-device-id",
                    builtIn = true,
                    metadata = SubscriptionMetadata(profileTitle = "server title"),
                    lastUpdatedAtMillis = 456L,
                ),
            ),
        )

        listOf(
            original.copy(id = original.id + 1),
            original.copy(url = "https://changed.example/subscription"),
            original.copy(userAgent = "different-agent"),
            original.copy(updateInterval = "12"),
            original.copy(ageSecretKey = "different-secret"),
            original.copy(updateViaProxy = !original.updateViaProxy),
            original.copy(enabled = !original.enabled),
        ).forEach { changedIdentity ->
            assertNotEquals(identity, subscriptionRefreshRequestIdentity(changedIdentity))
        }
    }

    @Test
    fun reconciliationUsesLatestAggregateAndLatestOverrideWhileMergingPartialMetadata() {
        val requested = subscription()
        val latest = requested.copy(
            title = "Current title",
            autoOverrideRules = false,
            metadata = SubscriptionMetadata(
                profileDescription = "Keep this description",
                announce = "Keep this announcement",
                trafficUploadBytes = 80L,
                trafficDownloadBytes = 120L,
                trafficTotalBytes = 500L,
                trafficExpireAtSeconds = 1_800L,
            ),
            lastUpdatedAtMillis = 100L,
        )
        val existing = listOf(
            ProxyServerRecord(11, requested.id, vless("match.example", "old name"), latency = "44 ms"),
            ProxyServerRecord(12, requested.id, Custom(remarks = "old custom", configJson = "{\"outbounds\":[]}"), latency = "31 ms"),
            ProxyServerRecord(20, 7, vless("unrelated.example", "unrelated"), latency = "9 ms"),
            ProxyServerRecord(
                30,
                -2,
                StrategyGroup(remarks = "strategy", proxyServerIds = listOf(11, 12), selectedMemberId = 12),
            ),
            ProxyServerRecord(31, -2, ChainProxy(remarks = "chain", proxyServerIds = listOf(11, 12))),
        )
        val snapshot = SubscriptionRefreshSnapshot(
            subscriptions = listOf(latest),
            servers = existing,
            nextServerId = 40,
            selectedServerId = 11,
        )
        val loaded = LoadedSubscriptionRefresh(
            sourceIdentity = subscriptionRefreshRequestIdentity(requested),
            urlCount = 3,
            servers = listOf(
                vless("match.example", "new name"),
                Custom(remarks = "renamed custom", overrideInboundAndDns = true, configJson = "{\"outbounds\":[]}"),
                vless("new.example", "new server"),
            ),
            metadata = SubscriptionMetadata(
                profileTitle = "Server title",
                profileUpdateIntervalHours = "12",
                userInfoReceived = false,
            ),
            resolvedEmbeddedConfig = ResolvedEmbeddedSubscriptionConfig(
                content = "{\"routing\":{}}",
                sourceUrl = "https://config.example/embedded",
                fallbackName = "Current title",
                activate = false,
            ),
        )

        val result = reconcileSubscriptionRefresh(
            snapshot = snapshot,
            loaded = loaded,
            refreshedAtMillis = 900L,
        )

        assertTrue(result.applicable)
        assertEquals("Server title", result.snapshot.subscriptions.single().title)
        assertEquals("Keep this description", result.snapshot.subscriptions.single().metadata?.profileDescription)
        assertEquals("Keep this announcement", result.snapshot.subscriptions.single().metadata?.announce)
        assertEquals("12", result.snapshot.subscriptions.single().metadata?.profileUpdateIntervalHours)
        assertEquals("12", result.snapshot.subscriptions.single().updateInterval)
        assertEquals(80L, result.snapshot.subscriptions.single().metadata?.trafficUploadBytes)
        assertEquals(120L, result.snapshot.subscriptions.single().metadata?.trafficDownloadBytes)
        assertEquals(500L, result.snapshot.subscriptions.single().metadata?.trafficTotalBytes)
        assertEquals(1_800L, result.snapshot.subscriptions.single().metadata?.trafficExpireAtSeconds)
        assertEquals(900L, result.snapshot.subscriptions.single().lastUpdatedAtMillis)

        val refreshed = result.snapshot.servers
        assertEquals(listOf(11, 12, 40, 20, 30, 31), refreshed.map(ProxyServerRecord::id))
        assertEquals("44 ms", refreshed.first { it.id == 11 }.latency)
        assertEquals("31 ms", refreshed.first { it.id == 12 }.latency)
        assertEquals("9 ms", refreshed.first { it.id == 20 }.latency)
        assertEquals(41, result.snapshot.nextServerId)
        assertEquals(11, result.snapshot.selectedServerId)
        assertEquals("new name", refreshed.first { it.id == 11 }.server.getInfo().remarks)
        assertFalse((refreshed.first { it.id == 12 }.server as Custom).overrideInboundAndDns)
        assertEquals(listOf(11, 12), (refreshed.first { it.id == 30 }.server as StrategyGroup).proxyServerIds)
        assertEquals(12, (refreshed.first { it.id == 30 }.server as StrategyGroup).selectedMemberId)
        assertEquals(listOf(11, 12), (refreshed.first { it.id == 31 }.server as ChainProxy).proxyServerIds)
        assertEquals(loaded.resolvedEmbeddedConfig, result.resolvedEmbeddedConfig)
    }

    @Test
    fun staleOrDeletedSubscriptionLeavesTheWholeAggregateUnchangedAndWithholdsEmbeddedConfig() {
        val requested = subscription()
        val current = requested.copy(url = "https://edited.example/subscription")
        val originalServer = ProxyServerRecord(50, 8, vless("manual.example", "manual"), latency = "12 ms")
        val snapshot = SubscriptionRefreshSnapshot(
            subscriptions = listOf(current),
            servers = listOf(originalServer),
            nextServerId = 80,
            selectedServerId = 50,
        )
        val loaded = loaded(requested)

        val changedResult = reconcileSubscriptionRefresh(
            snapshot = snapshot,
            loaded = loaded,
            refreshedAtMillis = 1_000L,
        )
        val deletedResult = reconcileSubscriptionRefresh(
            snapshot = snapshot.copy(subscriptions = emptyList()),
            loaded = loaded,
            refreshedAtMillis = 1_000L,
        )

        assertFalse(changedResult.applicable)
        assertEquals(snapshot, changedResult.snapshot)
        assertNull(changedResult.resolvedEmbeddedConfig)
        assertFalse(deletedResult.applicable)
        assertEquals(snapshot.copy(subscriptions = emptyList()), deletedResult.snapshot)
        assertNull(deletedResult.resolvedEmbeddedConfig)
    }

    @Test
    fun loadingUsesTheSameHostOptionsForRootProviderAndEmbeddedRequestsAndReturnsAndroidHandoff() = runTest {
        val request = SubscriptionRefreshLoadRequest(
            subscription = subscription(title = "Provider plan"),
            fetchOptions = RefreshOptions(
                sendDeviceHeaders = true,
                timeoutSeconds = 37,
                useRunningProxy = true,
                ageSecretKey = "age-key",
            ),
        )
        val calls = mutableListOf<FetchCall>()
        val providerUrl = "https://provider.example/list"
        val embeddedUrl = "https://config.example/profile.conf"
        lateinit var parser: ProxyServerPayloadParser
        parser = { text, context ->
            when (text) {
                "root payload" -> {
                    val providerPayload = context.providerUrlFetcher!!.fetch(providerUrl)
                    val providerResult = importProxyServersFromProviderPayload(
                        text = providerPayload,
                        parentContext = context,
                        providerUrl = providerUrl,
                        parsers = listOf(parser),
                    )
                    providerResult.copy(urlCount = providerResult.urlCount + 1)
                }
                "provider payload" -> ProxyServerImportResult(
                    urlCount = 1,
                    servers = listOf(vless("provider.example", "provider server")),
                )
                else -> EmptyProxyServerImportResult
            }
        }

        val loaded = loadSubscriptionRefresh(
            request = request,
            fetchResponse = { url, userAgent, options ->
                calls += FetchCall(url, userAgent, options)
                when (url) {
                    request.subscription.url -> SubscriptionFetchResponse(
                        body = "root payload",
                        headers = mapOf("routing" to "skipi://conf/add/$embeddedUrl"),
                    )
                    providerUrl -> SubscriptionFetchResponse(body = "provider payload")
                    embeddedUrl -> SubscriptionFetchResponse(body = "embedded profile content")
                    else -> error("Unexpected URL: $url")
                }
            },
            parsers = listOf(parser),
        )

        assertEquals(listOf(request.subscription.url, providerUrl, embeddedUrl), calls.map(FetchCall::url))
        assertTrue(calls.all { it.userAgent == request.subscription.userAgent })
        calls.forEach { assertSame(request.fetchOptions, it.options) }
        assertEquals(1, loaded.servers.size)
        assertEquals(2, loaded.urlCount)
        assertEquals("embedded profile content", loaded.resolvedEmbeddedConfig?.content)
        assertEquals(embeddedUrl, loaded.resolvedEmbeddedConfig?.sourceUrl)
        assertEquals("Provider plan", loaded.resolvedEmbeddedConfig?.fallbackName)
        assertFalse(loaded.resolvedEmbeddedConfig?.activate ?: true)
    }

    @Test
    fun inlineEmbeddedConfigUsesSubscriptionSourceAndAndroidFallbackName() = runTest {
        val request = SubscriptionRefreshLoadRequest(
            subscription = subscription(id = 73, title = ""),
            fetchOptions = RefreshOptions(),
        )
        val calls = mutableListOf<String>()
        val loaded = loadSubscriptionRefresh(
            request = request,
            fetchResponse = { url, _, _ ->
                calls += url
                SubscriptionFetchResponse(
                    body = "one proxy",
                    headers = mapOf("autorouting" to "e30="),
                )
            },
            parsers = listOf<ProxyServerPayloadParser>(
                { text, _ ->
                    if (text == "one proxy") ProxyServerImportResult(1, listOf(vless("one.example", "one")))
                    else EmptyProxyServerImportResult
                },
            ),
        )

        assertEquals(listOf(request.subscription.url), calls)
        assertEquals("{}", loaded.resolvedEmbeddedConfig?.content)
        assertEquals("subscription://73", loaded.resolvedEmbeddedConfig?.sourceUrl)
        assertEquals("Subscription Config", loaded.resolvedEmbeddedConfig?.fallbackName)
        assertTrue(loaded.resolvedEmbeddedConfig?.activate ?: false)
    }

    @Test
    fun embeddedRequestFailureIsBestEffortButCancellationPropagates() = runTest {
        val request = SubscriptionRefreshLoadRequest(
            subscription = subscription(),
            fetchOptions = RefreshOptions(),
        )
        val parser: ProxyServerPayloadParser = { text, _ ->
            if (text == "proxy") ProxyServerImportResult(1, listOf(vless("one.example", "one")))
            else EmptyProxyServerImportResult
        }
        val root = SubscriptionFetchResponse(
            body = "proxy",
            headers = mapOf("routing" to "https://config.example/profile"),
        )

        val bestEffort = loadSubscriptionRefresh(
            request = request,
            fetchResponse = { url, _, _ ->
                if (url == request.subscription.url) root else error("profile unavailable")
            },
            parsers = listOf(parser),
        )
        assertEquals(1, bestEffort.servers.size)
        assertNull(bestEffort.resolvedEmbeddedConfig)

        assertFailsWith<CancellationException> {
            loadSubscriptionRefresh(
                request = request,
                fetchResponse = { url, _, _ ->
                    if (url == request.subscription.url) root else throw CancellationException("cancelled")
                },
                parsers = listOf(parser),
            )
        }
    }

    @Test
    fun explicitProviderCancellationCaughtByParserFallsBackWhileCallerJobRemainsActive() = runTest {
        val request = SubscriptionRefreshLoadRequest(
            subscription = subscription(),
            fetchOptions = RefreshOptions(),
        )
        val parser: ProxyServerPayloadParser = { text, context ->
            if (text != "root") {
                EmptyProxyServerImportResult
            } else {
                try {
                    context.providerUrlFetcher!!.fetch("https://provider.example/list")
                    EmptyProxyServerImportResult
                } catch (_: Throwable) {
                    ProxyServerImportResult(1, listOf(vless("inline.example", "inline fallback")))
                }
            }
        }

        val loaded = loadSubscriptionRefresh(
            request = request,
            fetchResponse = { url, _, _ ->
                if (url == request.subscription.url) SubscriptionFetchResponse(body = "root")
                else throw CancellationException("provider callback cancelled")
            },
            parsers = listOf(parser),
        )

        assertEquals("inline fallback", loaded.servers.single().getInfo().remarks)
    }

    @Test
    fun callerJobCancellationDuringProviderParsingIsNotHiddenByParserFallback() = runTest {
        val request = SubscriptionRefreshLoadRequest(
            subscription = subscription(),
            fetchOptions = RefreshOptions(),
        )
        val providerStarted = CompletableDeferred<Unit>()
        var loadReturned = false
        val parser: ProxyServerPayloadParser = { text, context ->
            if (text != "root") {
                EmptyProxyServerImportResult
            } else {
                try {
                    context.providerUrlFetcher!!.fetch("https://provider.example/list")
                    EmptyProxyServerImportResult
                } catch (_: Throwable) {
                    ProxyServerImportResult(1, listOf(vless("inline.example", "inline fallback")))
                }
            }
        }
        val job = launch {
            loadSubscriptionRefresh(
                request = request,
                fetchResponse = { url, _, _ ->
                    if (url == request.subscription.url) {
                        SubscriptionFetchResponse(body = "root")
                    } else {
                        providerStarted.complete(Unit)
                        awaitCancellation()
                    }
                },
                parsers = listOf(parser),
            )
            loadReturned = true
        }

        providerStarted.await()
        job.cancelAndJoin()

        assertFalse(loadReturned)
    }

    @Test
    fun embeddedConfigIsResolvedBeforeAnEmptyServerImportFails() = runTest {
        val request = SubscriptionRefreshLoadRequest(
            subscription = subscription(),
            fetchOptions = RefreshOptions(),
        )
        val calls = mutableListOf<String>()

        assertFailsWith<IllegalArgumentException> {
            loadSubscriptionRefresh(
                request = request,
                fetchResponse = { url, _, _ ->
                    calls += url
                    if (url == request.subscription.url) {
                        SubscriptionFetchResponse(
                            body = "unrecognized subscription body",
                            headers = mapOf("routing" to "https://config.example/profile"),
                        )
                    } else {
                        SubscriptionFetchResponse(body = "profile resolved")
                    }
                },
                parsers = listOf<ProxyServerPayloadParser>({ _, _ -> EmptyProxyServerImportResult }),
            )
        }

        assertEquals(listOf(request.subscription.url, "https://config.example/profile"), calls)
    }

    private fun subscription(
        id: Int = 4,
        title: String = "Plan",
    ) = SubscriptionRecord(
        id = id,
        title = title,
        url = "https://subscription.example/list",
        userAgent = "Skipi/1 Android",
        updateInterval = "6",
        hwid = "device-id",
        ageSecretKey = "",
        updateViaProxy = false,
        autoOverrideRules = true,
        enabled = true,
        builtIn = false,
        metadata = null,
        lastUpdatedAtMillis = null,
        notifyOnExpiry = false,
        customExpiryReminders = null,
    )

    private fun loaded(subscription: SubscriptionRecord) = LoadedSubscriptionRefresh(
        sourceIdentity = subscriptionRefreshRequestIdentity(subscription),
        urlCount = 1,
        servers = listOf(vless("updated.example", "updated")),
        metadata = SubscriptionMetadata(profileTitle = "Updated"),
        resolvedEmbeddedConfig = ResolvedEmbeddedSubscriptionConfig(
            content = "embedded",
            sourceUrl = "subscription://${subscription.id}",
            fallbackName = subscription.title,
            activate = true,
        ),
    )

    private fun vless(host: String, remarks: String): VLESS = VLESS(
        remarks = remarks,
        id = "8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6",
        server = host,
        port = "443",
    )

    private data class RefreshOptions(
        val sendDeviceHeaders: Boolean = false,
        val timeoutSeconds: Int = 10,
        val useRunningProxy: Boolean = false,
        val ageSecretKey: String = "",
    )

    private data class FetchCall(
        val url: String,
        val userAgent: String,
        val options: RefreshOptions,
    )
}
