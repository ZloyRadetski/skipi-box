// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.ProxyServerState
import app.SubscriptionGroupState
import data.AndroidAppStateStore
import features.proxy.server.model.HTTP
import features.proxy.server.usecase.applyProxySubscriptionUpdates
import features.subscription.SubscriptionFetchResponse
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidSubscriptionRefreshBatchRunnerTest {
    @Test
    fun retainedRunnerSerializesReentryAndKeepsProgressThroughAggregateAndroidCommit() = runBlocking {
        val stateStore = cleanBatchRunnerStateStore()
        val groups = listOf(
            batchRunnerSubscription(id = 4, name = "Stored first"),
            batchRunnerSubscription(id = 5, name = "Stored second"),
        )
        stateStore.update(persist = false) { state ->
            state.copy(
                subscriptionGroups = groups,
                proxyServers = groups.mapIndexed { index, group ->
                    ProxyServerState(
                        id = 11 + index,
                        server = HTTP(
                            remarks = "Old ${group.id}",
                            server = "old-${group.id}.example.test",
                            port = "443",
                        ),
                        groupId = group.id,
                    )
                },
                nextProxyServerId = 13,
                selectedProxyServerId = 11,
                trafficConfigs = emptyList(),
                nextTrafficConfigId = 1,
                activeTrafficConfigId = 0,
            )
        }

        val fetchRelease = CompletableDeferred<Unit>()
        val firstCommitEntered = CompletableDeferred<Unit>()
        val firstCommitRelease = CompletableDeferred<Unit>()
        val captureCount = AtomicInteger()
        val eligibilityHookCount = AtomicInteger()
        val commitCount = AtomicInteger()
        val firstLoadEntered = CompletableDeferred<Unit>()
        lateinit var runner: AndroidSubscriptionRefreshBatchRunner
        runner = AndroidSubscriptionRefreshBatchRunner(
            captureGroups = {
                captureCount.incrementAndGet()
                stateStore.state.value.subscriptionGroups
            },
            onBeforeEligibility = { eligibilityHookCount.incrementAndGet() },
            fetchOptions = { batchRunnerFetchOptions },
            fetchResponse = { url, _, _ ->
                if (url.endsWith("/4") || url.endsWith("/5")) {
                    firstLoadEntered.complete(Unit)
                    fetchRelease.await()
                }
                val id = url.substringAfterLast('/').toInt()
                SubscriptionFetchResponse(body = refreshedSubscriptionBody(id))
            },
            refreshedAtMillis = { 1_500L + captureCount.get() },
            onCommitUpdates = { updates, refreshedAtMillis ->
                val currentCall = commitCount.incrementAndGet()
                assertEquals(2, updates.size)
                assertEquals(setOf(4, 5), runner.progress.value.refreshingSubscriptionIds)
                if (currentCall == 1) {
                    firstCommitEntered.complete(Unit)
                    firstCommitRelease.await()
                }
                applyProxySubscriptionUpdates(
                    stateStore = stateStore,
                    updates = updates,
                    updatedAtMillis = refreshedAtMillis,
                    updateAppState = { transform -> stateStore.update(persist = false, transform = transform) },
                )
            },
        )

        try {
            val first = async(start = CoroutineStart.UNDISPATCHED) { runner.run() }
            firstLoadEntered.await()
            assertEquals(setOf(4, 5), runner.progress.value.refreshingSubscriptionIds)

            val reentered = async(start = CoroutineStart.UNDISPATCHED) { runner.run() }
            yield()
            assertEquals(1, captureCount.get())
            assertEquals(1, eligibilityHookCount.get())

            fetchRelease.complete(Unit)
            firstCommitEntered.await()
            assertEquals(listOf("Stored first", "Stored second"), stateStore.state.value.subscriptionGroups.map { it.name })
            assertEquals(setOf(4, 5), runner.progress.value.refreshingSubscriptionIds)
            assertEquals(1, captureCount.get())

            firstCommitRelease.complete(Unit)
            val firstResult = first.await()
            val secondResult = reentered.await()

            assertEquals(listOf(4, 5), firstResult.updates.map { it.groupId })
            assertEquals(listOf(4, 5), secondResult.updates.map { it.groupId })
            assertEquals(2, captureCount.get())
            assertEquals(2, eligibilityHookCount.get())
            assertEquals(2, commitCount.get())
            assertEquals(emptySet(), runner.progress.value.refreshingSubscriptionIds)
            assertEquals(
                listOf("Refreshed 4", "Refreshed 5"),
                stateStore.state.value.subscriptionGroups.map { it.name },
            )
            assertEquals(
                listOf("Refreshed server 4", "Refreshed server 5"),
                stateStore.state.value.proxyServers
                    .filter { it.groupId == 4 || it.groupId == 5 }
                    .map { it.server.getInfo().remarks },
            )
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun releaseCheckHookRunsBeforeEligibilityAndCaptureEvenWhenNoGroupIsEligible() = runBlocking {
        val order = mutableListOf<String>()
        val ineligible = listOf(
            batchRunnerSubscription(id = 4, name = "Disabled").copy(enabled = false),
            batchRunnerSubscription(id = 5, name = "Blank URL").copy(url = "  "),
        )
        val runner = AndroidSubscriptionRefreshBatchRunner(
            captureGroups = { order += "capture"; ineligible },
            onBeforeEligibility = { order += "release-check" },
            fetchOptions = { error("No ineligible request should derive fetch options") },
            fetchResponse = { _, _, _ -> error("No ineligible request should load") },
            refreshedAtMillis = { 1_600L },
            onCommitUpdates = { _, _ -> error("An empty batch should not commit") },
        )

        val result = runner.run()

        assertEquals(listOf("release-check", "capture"), order)
        assertEquals(0, result.updatedGroupCount)
        assertEquals(0, result.failedGroupCount)
        assertEquals(emptySet(), runner.progress.value.refreshingSubscriptionIds)
    }

    @Test
    fun oneSuccessfulLoadUsesTheExistingSingleCommitPathAndKeepsFetchFailureSeparate() = runBlocking {
        val stateStore = cleanBatchRunnerStateStore()
        val successful = batchRunnerSubscription(id = 4, name = "Stored first")
        val failed = batchRunnerSubscription(id = 5, name = "Stored second")
        stateStore.update(persist = false) { state -> state.copy(subscriptionGroups = listOf(successful, failed)) }
        var commits = 0
        val runner = AndroidSubscriptionRefreshBatchRunner(
            captureGroups = { stateStore.state.value.subscriptionGroups },
            onBeforeEligibility = {},
            fetchOptions = { batchRunnerFetchOptions },
            fetchResponse = { url, _, _ ->
                if (url.endsWith("/5")) error("injected fetch failure")
                SubscriptionFetchResponse(body = refreshedSubscriptionBody(4))
            },
            refreshedAtMillis = { 1_700L },
            onCommitUpdates = { updates, refreshedAtMillis ->
                commits += 1
                assertEquals(listOf(4), updates.map { it.groupId })
                applyProxySubscriptionUpdates(
                    stateStore = stateStore,
                    updates = updates,
                    updatedAtMillis = refreshedAtMillis,
                    updateAppState = { transform -> stateStore.update(persist = false, transform = transform) },
                )
            },
        )

        try {
            val result = runner.run()

            assertEquals(listOf(4), result.updates.map { it.groupId })
            assertEquals(listOf(5), result.failures.map { it.groupId })
            assertEquals(1, commits)
            assertEquals(
                "Refreshed 4",
                stateStore.state.value.subscriptionGroups.first { it.id == 4 }.name,
            )
            assertEquals("Stored second", stateStore.state.value.subscriptionGroups.first { it.id == 5 }.name)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun fetchOptionsAreDerivedOnlyAfterTheSharedPerSubscriptionLockIsAcquired() = runBlocking {
        val group = batchRunnerSubscription(id = 4, name = "Stored first")
        val coordinator = SubscriptionUpdateCoordinator()
        val singleFetchEntered = CompletableDeferred<Unit>()
        val releaseSingleFetch = CompletableDeferred<String>()
        val initialOptions = batchRunnerFetchOptions.copy(timeoutSeconds = 17)
        var currentBatchOptions = batchRunnerFetchOptions.copy(timeoutSeconds = 29)
        var batchOptionReads = 0
        var batchOptionsSeen: AndroidSubscriptionFetchOptions? = null

        val single = async(start = CoroutineStart.UNDISPATCHED) {
            updateSubscriptions(
                groups = listOf(group),
                fetchOptions = { initialOptions },
                fetchText = { _, _, options ->
                    assertEquals(initialOptions, options)
                    singleFetchEntered.complete(Unit)
                    releaseSingleFetch.await()
                },
                coordinator = coordinator,
            )
        }
        singleFetchEntered.await()

        val batchRunner = AndroidSubscriptionRefreshBatchRunner(
            captureGroups = { listOf(group) },
            onBeforeEligibility = {},
            fetchOptions = {
                batchOptionReads += 1
                currentBatchOptions
            },
            fetchResponse = { _, _, options ->
                batchOptionsSeen = options
                SubscriptionFetchResponse(body = refreshedSubscriptionBody(4))
            },
            refreshedAtMillis = { 1_800L },
            onCommitUpdates = { _, _ -> },
            coordinator = coordinator,
        )
        val batch = async(start = CoroutineStart.UNDISPATCHED) { batchRunner.run() }
        yield()

        assertEquals(0, batchOptionReads)
        currentBatchOptions = batchRunnerFetchOptions.copy(timeoutSeconds = 41)
        releaseSingleFetch.complete(refreshedSubscriptionBody(4))
        single.await()
        batch.await()

        assertEquals(1, batchOptionReads)
        assertEquals(currentBatchOptions, batchOptionsSeen)
        assertTrue(batchRunner.progress.value.refreshingSubscriptionIds.isEmpty())
    }
}

private suspend fun cleanBatchRunnerStateStore(): AndroidAppStateStore {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return AndroidAppStateStore.get(context).also { it.resetToStockState() }
}

private fun batchRunnerSubscription(id: Int, name: String) = SubscriptionGroupState(
    id = id,
    name = name,
    url = "https://subscription.example.test/$id",
    userAgent = "Skipi/1 Android",
    updateInterval = "6",
    enabled = true,
)

private fun refreshedSubscriptionBody(id: Int) = """
    #profile-title: Refreshed $id
    #profile-update-interval: 18
    proxies:
      - name: Refreshed server $id
        type: vless
        server: refreshed-$id.example.test
        port: 443
        uuid: 11111111-1111-4111-8111-111111111111
""".trimIndent()

private val batchRunnerFetchOptions = AndroidSubscriptionFetchOptions(
    useRunningProxy = false,
    ageSecretKey = "",
    sendDeviceHeaders = false,
    timeoutSeconds = 12,
)
