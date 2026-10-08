// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.ProxyServerRecord
import features.proxy.server.model.Custom
import features.proxy.server.model.VLESS
import features.proxy.server.usecase.ProxyServerImportResult
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.subscription.SubscriptionFetchResponse
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.Continuation
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionRefreshCommitPortTest {
    @Test
    fun refreshSubscriptionFetchesAndParsesBeforeClockAndOrderedCommit() = runTest {
        val requested = subscription()
        val responseBody = "vless://uuid@fresh.example:443?encryption=none#Fresh"
        val timeline = mutableListOf<String>()
        val port = RecordingPort(snapshot(requested)).apply {
            beforeServerTransform = { timeline += "servers" }
            beforeSubscriptionTransform = { current ->
                timeline += "subscription"
                current
            }
        }
        val parser: ProxyServerPayloadParser = { payload, _ ->
            timeline += "parse"
            assertEquals(responseBody, payload)
            ProxyServerImportResult(urlCount = 1, servers = listOf(vless("fresh.example", "Fresh")))
        }
        val fetchStarted = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        var result: SubscriptionRefreshResult? = null
        val job = launch {
            result = refreshSubscription(
                request = SubscriptionRefreshLoadRequest(
                    subscription = requested,
                    fetchOptions = "opaque fetch options",
                ),
                fetchResponse = { url, userAgent, options ->
                    assertEquals(requested.url, url)
                    assertEquals(requested.userAgent, userAgent)
                    assertEquals("opaque fetch options", options)
                    timeline += "fetch-start"
                    fetchStarted.complete(Unit)
                    releaseFetch.await()
                    timeline += "fetch-complete"
                    SubscriptionFetchResponse(
                        body = responseBody,
                        headers = mapOf("profile-title" to "Loaded title"),
                    )
                },
                parsers = listOf(parser),
                refreshedAtMillis = {
                    timeline += "clock"
                    1_234L
                },
                port = port,
            )
        }

        fetchStarted.await()
        assertFalse("clock" in timeline)
        releaseFetch.complete(Unit)
        job.join()

        assertEquals(
            listOf("fetch-start", "fetch-complete", "parse", "clock", "servers", "subscription"),
            timeline,
        )
        val refreshResult = requireNotNull(result)
        assertTrue(refreshResult.commit.applicable)
        assertEquals(
            listOf(SubscriptionRefreshCommitScope.SERVERS, SubscriptionRefreshCommitScope.SUBSCRIPTION),
            refreshResult.commit.appliedScopes,
        )
        assertEquals(1, refreshResult.loaded.urlCount)
        assertEquals(1, refreshResult.loaded.servers.size)
        assertEquals("Loaded title", refreshResult.loaded.metadata.profileTitle)
        assertEquals("Loaded title", port.savedSubscription?.title)
        assertEquals(1_234L, port.savedSubscription?.lastUpdatedAtMillis)
    }

    @Test
    fun alreadyCancelledCallerDoesNotStartTheFirstPersistenceStep() {
        val requested = subscription()
        val port = RecordingPort(snapshot(requested))
        val cancelledJob = Job().apply { cancel() }
        var completion: Result<SubscriptionRefreshCommitResult>? = null

        suspend {
            commitSubscriptionRefresh(
                loaded = loaded(requested),
                refreshedAtMillis = 1_000L,
                port = port,
            )
        }.startCoroutine(object : Continuation<SubscriptionRefreshCommitResult> {
            override val context = cancelledJob

            override fun resumeWith(result: Result<SubscriptionRefreshCommitResult>) {
                completion = result
            }
        })

        assertTrue(requireNotNull(completion).exceptionOrNull() is CancellationException)
        assertTrue(port.events.isEmpty())
        assertTrue(port.savedServers.isEmpty())
    }

    @Test
    fun commitReconcilesInsideLatestServerTransformThenUpdatesCurrentMetadataAndProfile() = runTest {
        val requested = subscription()
        val port = RecordingPort(snapshot(requested))
        val serverTransformEntered = CompletableDeferred<Unit>()
        val releaseServerTransform = CompletableDeferred<Unit>()
        port.beforeServerTransform = {
            serverTransformEntered.complete(Unit)
            releaseServerTransform.await()
        }
        port.beforeSubscriptionTransform = { current ->
            current?.copy(
                title = "Renamed after server save",
                metadata = SubscriptionMetadata(
                    profileDescription = "Keep latest description",
                    profileUpdateIntervalHours = current.updateInterval,
                ),
            )
        }
        val refresh = loaded(requested).let { loaded ->
            loaded.copy(metadata = loaded.metadata.copy(profileTitle = null))
        }
        var result: SubscriptionRefreshCommitResult? = null
        val job = launch {
            result = commitSubscriptionRefresh(
                loaded = refresh,
                refreshedAtMillis = 900L,
                port = port,
            )
        }

        serverTransformEntered.await()
        val unrelated = ProxyServerRecord(
            id = 27,
            groupId = 9,
            server = vless("manual.example", "Manual"),
            latency = "14 ms",
        )
        port.latest = port.latest.copy(
            servers = port.latest.servers + unrelated,
            nextServerId = 28,
            selectedServerId = 27,
            subscriptions = port.latest.subscriptions.map { it.copy(autoOverrideRules = false) },
        )
        releaseServerTransform.complete(Unit)
        job.join()

        val receipt = requireNotNull(result)
        assertTrue(receipt.applicable)
        assertEquals(
            listOf(
                SubscriptionRefreshCommitScope.SERVERS,
                SubscriptionRefreshCommitScope.SUBSCRIPTION,
                SubscriptionRefreshCommitScope.PROFILE,
            ),
            receipt.appliedScopes,
        )
        assertNull(receipt.failure)
        assertEquals(listOf("servers", "subscription", "profile"), port.events)
        assertTrue(port.savedServers.any { it.id == unrelated.id })
        assertEquals("14 ms", port.savedServers.single { it.id == unrelated.id }.latency)
        assertEquals(27, port.savedSelectedServerId)
        assertEquals(30, port.savedNextServerId)
        assertFalse(port.savedServers.single { it.server is Custom }.server.let { it as Custom }.overrideInboundAndDns)
        assertEquals("Renamed after server save", port.savedSubscription?.title)
        assertEquals("Keep latest description", port.savedSubscription?.metadata?.profileDescription)
        assertEquals("https://support.example/help", port.savedSubscription?.metadata?.supportUrl)
        assertEquals(900L, port.savedSubscription?.lastUpdatedAtMillis)
        assertEquals(refresh.resolvedEmbeddedConfig, port.appliedProfile)
    }

    @Test
    fun identityChangeBetweenCatalogAndMetadataStepsLeavesServerStepAppliedAndWithholdsProfile() = runTest {
        val requested = subscription()
        val port = RecordingPort(snapshot(requested))
        port.beforeSubscriptionTransform = { current ->
            current?.copy(url = "https://edited.example/subscription")
        }

        val result = commitSubscriptionRefresh(
            loaded = loaded(requested),
            refreshedAtMillis = 1_000L,
            port = port,
        )

        assertFalse(result.applicable)
        assertEquals(listOf(SubscriptionRefreshCommitScope.SERVERS), result.appliedScopes)
        assertNull(result.failure)
        assertEquals(listOf("servers", "subscription"), port.events)
        assertTrue(port.savedServers.isNotEmpty())
        assertEquals("https://edited.example/subscription", port.latest.subscriptions.single().url)
        assertNull(port.appliedProfile)
    }

    @Test
    fun serverSaveFailureReportsNoAppliedScopeAndDoesNotStartLaterSteps() = runTest {
        val port = RecordingPort(snapshot(subscription())).apply {
            failOn = SubscriptionRefreshCommitScope.SERVERS
        }

        val result = commitSubscriptionRefresh(
            loaded = loaded(subscription()),
            refreshedAtMillis = 1_000L,
            port = port,
        )

        assertTrue(result.applicable)
        assertTrue(result.appliedScopes.isEmpty())
        assertEquals(SubscriptionRefreshCommitScope.SERVERS, result.failure?.scope)
        assertEquals("failed servers", result.failure?.error?.message)
        assertEquals(listOf("servers"), port.events)
        assertNull(port.savedSubscription)
        assertNull(port.appliedProfile)
    }

    @Test
    fun metadataSaveFailureReportsServerScopeAsAlreadyApplied() = runTest {
        val port = RecordingPort(snapshot(subscription())).apply {
            failOn = SubscriptionRefreshCommitScope.SUBSCRIPTION
        }

        val result = commitSubscriptionRefresh(
            loaded = loaded(subscription()),
            refreshedAtMillis = 1_000L,
            port = port,
        )

        assertTrue(result.applicable)
        assertEquals(listOf(SubscriptionRefreshCommitScope.SERVERS), result.appliedScopes)
        assertEquals(SubscriptionRefreshCommitScope.SUBSCRIPTION, result.failure?.scope)
        assertEquals("failed subscription", result.failure?.error?.message)
        assertEquals(listOf("servers", "subscription"), port.events)
        assertTrue(port.savedServers.isNotEmpty())
        assertNull(port.savedSubscription)
        assertNull(port.appliedProfile)
    }

    @Test
    fun profileSaveFailureReportsServersAndMetadataAsAlreadyApplied() = runTest {
        val port = RecordingPort(snapshot(subscription())).apply {
            failOn = SubscriptionRefreshCommitScope.PROFILE
        }

        val result = commitSubscriptionRefresh(
            loaded = loaded(subscription()),
            refreshedAtMillis = 1_000L,
            port = port,
        )

        assertTrue(result.applicable)
        assertEquals(
            listOf(SubscriptionRefreshCommitScope.SERVERS, SubscriptionRefreshCommitScope.SUBSCRIPTION),
            result.appliedScopes,
        )
        assertEquals(SubscriptionRefreshCommitScope.PROFILE, result.failure?.scope)
        assertEquals("failed profile", result.failure?.error?.message)
        assertEquals(listOf("servers", "subscription", "profile"), port.events)
        assertTrue(port.savedServers.isNotEmpty())
        assertEquals("Updated title", port.savedSubscription?.title)
        assertNull(port.appliedProfile)
    }

    @Test
    fun cancellationDuringMetadataStepPropagatesWithoutApplyingProfile() = runTest {
        val port = RecordingPort(snapshot(subscription())).apply {
            cancelOn = SubscriptionRefreshCommitScope.SUBSCRIPTION
        }

        assertFailsWith<CancellationException> {
            commitSubscriptionRefresh(
                loaded = loaded(subscription()),
                refreshedAtMillis = 1_000L,
                port = port,
            )
        }

        assertEquals(listOf("servers", "subscription"), port.events)
        assertTrue(port.savedServers.isNotEmpty())
        assertNull(port.savedSubscription)
        assertNull(port.appliedProfile)
    }

    @Test
    fun jobCancelledByServerCallbackStopsBeforeMetadataAndProfile() = runTest {
        val requested = subscription()
        val port = RecordingPort(snapshot(requested)).apply {
            cancelCurrentJobAfterServerResult = true
        }

        val job = launch {
            commitSubscriptionRefresh(
                loaded = loaded(requested),
                refreshedAtMillis = 1_000L,
                port = port,
            )
        }
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(listOf("servers"), port.events)
        assertTrue(port.savedServers.isNotEmpty())
        assertNull(port.savedSubscription)
        assertNull(port.appliedProfile)
    }

    @Test
    fun deletedTargetIsInapplicableWithoutAnyCommitStep() = runTest {
        val requested = subscription()
        val port = RecordingPort(snapshot(requested).copy(subscriptions = emptyList()))

        val result = commitSubscriptionRefresh(
            loaded = loaded(requested),
            refreshedAtMillis = 1_000L,
            port = port,
        )

        assertFalse(result.applicable)
        assertTrue(result.appliedScopes.isEmpty())
        assertNull(result.failure)
        assertEquals(listOf("servers"), port.events)
        assertTrue(port.savedServers.isEmpty())
        assertNull(port.savedSubscription)
        assertNull(port.appliedProfile)
    }

    @Test
    fun noEmbeddedHandoffStopsAfterCurrentMetadataUpdate() = runTest {
        val requested = subscription()
        val port = RecordingPort(snapshot(requested))

        val result = commitSubscriptionRefresh(
            loaded = loaded(requested).copy(resolvedEmbeddedConfig = null),
            refreshedAtMillis = 1_000L,
            port = port,
        )

        assertTrue(result.applicable)
        assertEquals(
            listOf(SubscriptionRefreshCommitScope.SERVERS, SubscriptionRefreshCommitScope.SUBSCRIPTION),
            result.appliedScopes,
        )
        assertNull(result.failure)
        assertEquals(listOf("servers", "subscription"), port.events)
        assertNull(port.appliedProfile)
    }

    @Test
    fun ignoredEmbeddedHandoffDoesNotReportProfileAsApplied() = runTest {
        val requested = subscription()
        val port = RecordingPort(snapshot(requested)).apply {
            profileApplied = false
        }

        val result = commitSubscriptionRefresh(
            loaded = loaded(requested),
            refreshedAtMillis = 1_000L,
            port = port,
        )

        assertTrue(result.applicable)
        assertEquals(
            listOf(SubscriptionRefreshCommitScope.SERVERS, SubscriptionRefreshCommitScope.SUBSCRIPTION),
            result.appliedScopes,
        )
        assertNull(result.failure)
        assertEquals(listOf("servers", "subscription", "profile"), port.events)
        assertNull(port.appliedProfile)
    }

    private fun snapshot(subscription: SubscriptionRecord) = SubscriptionRefreshSnapshot(
        subscriptions = listOf(subscription),
        servers = listOf(
            ProxyServerRecord(
                id = 10,
                groupId = subscription.id,
                server = vless("old.example", "Old subscription server"),
                latency = "42 ms",
            ),
        ),
        nextServerId = 20,
        selectedServerId = 10,
    )

    private fun loaded(subscription: SubscriptionRecord) = LoadedSubscriptionRefresh(
        sourceIdentity = subscriptionRefreshRequestIdentity(subscription),
        urlCount = 2,
        servers = listOf(
            Custom(remarks = "Custom from subscription", overrideInboundAndDns = true, configJson = "{\"outbounds\":[]}"),
            vless("new.example", "New subscription server"),
        ),
        metadata = SubscriptionMetadata(
            profileTitle = "Updated title",
            supportUrl = "https://support.example/help",
        ),
        resolvedEmbeddedConfig = ResolvedEmbeddedSubscriptionConfig(
            content = "[General]\nmode = rule",
            sourceUrl = "subscription://${subscription.id}",
            fallbackName = "Subscription Config",
            activate = true,
        ),
    )

    private fun subscription() = SubscriptionRecord(
        id = 4,
        title = "Original title",
        url = "https://subscription.example/list",
        userAgent = "Skipi/1 Android",
        updateInterval = "6",
        hwid = "device-id",
        ageSecretKey = "",
        updateViaProxy = false,
        autoOverrideRules = true,
        enabled = true,
        builtIn = false,
        metadata = SubscriptionMetadata(profileDescription = "Stored description"),
        lastUpdatedAtMillis = 100L,
        notifyOnExpiry = false,
        customExpiryReminders = null,
    )

    private fun vless(host: String, remarks: String) = VLESS(
        remarks = remarks,
        id = "8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6",
        server = host,
        port = "443",
    )

    private class RecordingPort(
        var latest: SubscriptionRefreshSnapshot,
    ) : SubscriptionRefreshCommitPort {
        val events = mutableListOf<String>()
        var savedServers = emptyList<ProxyServerRecord>()
        var savedNextServerId: Int? = null
        var savedSelectedServerId: Int? = null
        var savedSubscription: SubscriptionRecord? = null
        var appliedProfile: ResolvedEmbeddedSubscriptionConfig? = null
        var profileApplied = true
        var failOn: SubscriptionRefreshCommitScope? = null
        var cancelOn: SubscriptionRefreshCommitScope? = null
        var cancelCurrentJobAfterServerResult = false
        var beforeServerTransform: suspend () -> Unit = {}
        var beforeSubscriptionTransform: (SubscriptionRecord?) -> SubscriptionRecord? = { it }

        override suspend fun updateServers(
            reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshReconciliationResult,
        ): SubscriptionRefreshReconciliationResult {
            events += "servers"
            beforeServerTransform()
            if (cancelOn == SubscriptionRefreshCommitScope.SERVERS) throw CancellationException("cancelled servers")
            if (failOn == SubscriptionRefreshCommitScope.SERVERS) error("failed servers")
            val result = reconcileLatest(latest)
            if (result.applicable) {
                savedServers = result.snapshot.servers
                savedNextServerId = result.snapshot.nextServerId
                savedSelectedServerId = result.snapshot.selectedServerId
                latest = latest.copy(
                    servers = savedServers,
                    nextServerId = result.snapshot.nextServerId,
                    selectedServerId = result.snapshot.selectedServerId,
                )
            }
            if (cancelCurrentJobAfterServerResult) {
                currentCoroutineContext()[Job]?.cancel(CancellationException("cancelled by server callback"))
            }
            return result
        }

        override suspend fun updateSubscription(
            subscriptionId: Int,
            transform: (SubscriptionRecord?) -> SubscriptionRecord?,
        ): SubscriptionRecord? {
            events += "subscription"
            if (cancelOn == SubscriptionRefreshCommitScope.SUBSCRIPTION) throw CancellationException("cancelled subscription")
            if (failOn == SubscriptionRefreshCommitScope.SUBSCRIPTION) error("failed subscription")
            val current = latest.subscriptions.firstOrNull { it.id == subscriptionId }
            val latestAtTransform = beforeSubscriptionTransform(current)
            latest = latest.copy(
                subscriptions = latest.subscriptions.map { record ->
                    if (record.id == subscriptionId) latestAtTransform ?: record else record
                },
            )
            val updated = transform(latestAtTransform)
            if (updated != null) {
                savedSubscription = updated
                latest = latest.copy(
                    subscriptions = latest.subscriptions.map { record ->
                        if (record.id == subscriptionId) updated else record
                    },
                )
            }
            return updated
        }

        override suspend fun applyEmbeddedConfig(config: ResolvedEmbeddedSubscriptionConfig): Boolean {
            events += "profile"
            if (cancelOn == SubscriptionRefreshCommitScope.PROFILE) throw CancellationException("cancelled profile")
            if (failOn == SubscriptionRefreshCommitScope.PROFILE) error("failed profile")
            if (profileApplied) appliedProfile = config
            return profileApplied
        }
    }
}
