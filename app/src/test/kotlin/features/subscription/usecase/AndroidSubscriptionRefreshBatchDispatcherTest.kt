// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.usecase

import app.SubscriptionGroupState
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdate
import features.subscription.SubscriptionFetchResponse
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AndroidSubscriptionRefreshBatchDispatcherTest {
    @Test
    fun retainedBatchRunnerLoadsAndParsesAwayFromCallerButCapturesAndCommitsOnCaller() = runBlocking {
        val uiLikeDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val eligible = dispatcherSubscription(id = 41)
            val disabled = dispatcherSubscription(id = 42, enabled = false)
            val blankUrl = dispatcherSubscription(id = 43, url = "  ")
            val options = AndroidSubscriptionFetchOptions(
                useRunningProxy = false,
                ageSecretKey = "",
                sendDeviceHeaders = false,
                timeoutSeconds = 19,
            )
            val callerThread = withContext(uiLikeDispatcher) { Thread.currentThread() }
            val eligibilityHookThread = AtomicReference<Thread?>()
            val captureThread = AtomicReference<Thread?>()
            val fetchOptionsThread = AtomicReference<Thread?>()
            val fetchResponseThread = AtomicReference<Thread?>()
            val timestampThread = AtomicReference<Thread?>()
            val commitThread = AtomicReference<Thread?>()
            val optionGroupId = AtomicReference<Int?>()
            val fetchedUrl = AtomicReference<String?>()
            lateinit var runner: AndroidSubscriptionRefreshBatchRunner
            runner = AndroidSubscriptionRefreshBatchRunner(
                captureGroups = {
                    captureThread.set(Thread.currentThread())
                    listOf(eligible, disabled, blankUrl)
                },
                onBeforeEligibility = {
                    eligibilityHookThread.set(Thread.currentThread())
                },
                fetchOptions = { group ->
                    optionGroupId.set(group.id)
                    fetchOptionsThread.set(Thread.currentThread())
                    options
                },
                fetchResponse = { url, userAgent, requestOptions ->
                    fetchedUrl.set(url)
                    fetchResponseThread.set(Thread.currentThread())
                    assertEquals(eligible.url, url)
                    assertEquals(eligible.userAgent, userAgent)
                    assertEquals(options, requestOptions)
                    SubscriptionFetchResponse(body = androidRefreshYaml(eligible.id))
                },
                refreshedAtMillis = {
                    timestampThread.set(Thread.currentThread())
                    2_400L
                },
                onCommitUpdates = { updates, refreshedAtMillis ->
                    commitThread.set(Thread.currentThread())
                    assertEquals(2_400L, refreshedAtMillis)
                    assertEquals(listOf(eligible.id), updates.map(ProxyServerListSubscriptionUpdate::groupId))
                    assertEquals(
                        setOf(eligible.id),
                        runner.progress.value.refreshingSubscriptionIds,
                    )
                },
                coordinator = SubscriptionUpdateCoordinator(),
            )

            val result = withContext(uiLikeDispatcher) { runner.run() }

            assertSame(callerThread, eligibilityHookThread.get())
            assertSame(callerThread, captureThread.get())
            assertSame(callerThread, timestampThread.get())
            assertSame(callerThread, commitThread.get())
            assertNotSame(callerThread, fetchOptionsThread.get())
            assertNotSame(callerThread, fetchResponseThread.get())
            assertEquals(eligible.id, optionGroupId.get())
            assertEquals(eligible.url, fetchedUrl.get())
            assertEquals(listOf(eligible.id), result.updates.map(ProxyServerListSubscriptionUpdate::groupId))
            assertEquals(emptyList(), result.failures)
            assertEquals(2_400L, result.updatedAtMillis)
            assertEquals(1, result.updates.single().urlCount)
            assertEquals(
                "Android parsed server ${eligible.id}",
                result.updates.single().servers.single().getInfo().remarks,
            )
            assertEquals(emptySet(), runner.progress.value.refreshingSubscriptionIds)
        } finally {
            uiLikeDispatcher.close()
        }
    }

    @Test
    fun reachableSingleRefreshLoaderAlsoFetchesAndParsesAwayFromCaller() = runBlocking {
        val uiLikeDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val subscription = dispatcherSubscription(id = 52)
            val options = AndroidSubscriptionFetchOptions(
                useRunningProxy = false,
                ageSecretKey = "",
                sendDeviceHeaders = false,
                timeoutSeconds = 23,
            )
            val callerThread = withContext(uiLikeDispatcher) { Thread.currentThread() }
            val fetchOptionsThread = AtomicReference<Thread?>()
            val fetchTextThread = AtomicReference<Thread?>()

            val result = withContext(uiLikeDispatcher) {
                updateSubscriptions(
                    groups = listOf(subscription),
                    fetchOptions = {
                        fetchOptionsThread.set(Thread.currentThread())
                        options
                    },
                    fetchText = { url, userAgent, requestOptions ->
                        fetchTextThread.set(Thread.currentThread())
                        assertEquals(subscription.url, url)
                        assertEquals(subscription.userAgent, userAgent)
                        assertEquals(options, requestOptions)
                        androidRefreshYaml(subscription.id)
                    },
                    coordinator = SubscriptionUpdateCoordinator(),
                )
            }

            assertNotSame(callerThread, fetchOptionsThread.get())
            assertNotSame(callerThread, fetchTextThread.get())
            assertEquals(listOf(subscription.id), result.updates.map(ProxyServerListSubscriptionUpdate::groupId))
            assertEquals(emptyList(), result.failures)
            assertEquals(
                "Android parsed server ${subscription.id}",
                result.updates.single().servers.single().getInfo().remarks,
            )
        } finally {
            uiLikeDispatcher.close()
        }
    }
}

private fun dispatcherSubscription(
    id: Int,
    url: String = "https://subscription.example.test/$id",
    enabled: Boolean = true,
) = SubscriptionGroupState(
    id = id,
    name = "Stored $id",
    url = url,
    userAgent = "Skipi/1 Android",
    updateInterval = "6",
    enabled = enabled,
)

private fun androidRefreshYaml(id: Int): String = """
    #profile-title: Parsed by Android
    proxies:
      - name: Android parsed server $id
        type: vless
        server: refreshed-$id.example.test
        port: 443
        uuid: 11111111-1111-4111-8111-111111111111
""".trimIndent()
