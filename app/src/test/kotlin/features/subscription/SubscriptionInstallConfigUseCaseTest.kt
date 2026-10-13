// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.AppState
import app.SubscriptionGroupState
import data.AndroidAppStateStore
import features.subscription.runtime.AndroidSubscriptionFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import com.sun.net.httpserver.HttpServer
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SubscriptionInstallConfigUseCaseTest {
    @Test
    fun new_group_is_visible_before_fetch_and_survives_fetch_failure() = runBlocking {
        val stateStore = cleanStateStore()
        val initialState = stateStore.currentState
        val stateAtFetch = AtomicReference<AppState?>()

        try {
            withFailingSubscriptionServer(
                onRequest = { stateAtFetch.set(stateStore.currentState) },
            ) { url ->
                val result = SubscriptionInstallConfigUseCase(
                    stateStore = stateStore,
                    subscriptionFetcher = AndroidSubscriptionFetcher("install-test", TestAgeCrypto),
                ).install(
                    SubscriptionInstallConfig(
                        name = "Imported group",
                        url = url,
                        userAgent = "Imported UA",
                    ),
                )

                val storedGroup = stateStore.currentState.subscriptionGroups.last()
                assertEquals("Imported group", storedGroup.name)
                assertEquals(url, storedGroup.url)
                assertEquals("Imported UA", storedGroup.userAgent)
                assertEquals(initialState.nextSubscriptionGroupId, storedGroup.id)
                assertEquals(initialState.nextSubscriptionGroupId + 1, stateStore.currentState.nextSubscriptionGroupId)
                assertEquals("", storedGroup.updateInterval)
                assertEquals(false, storedGroup.updateViaProxy)
                assertEquals(true, storedGroup.enabled)
                assertEquals(true, storedGroup.notifyOnExpiry)
                assertEquals(null, storedGroup.customExpiryReminders)
                assertEquals(initialState.subscriptionGroups + storedGroup, stateStore.currentState.subscriptionGroups)

                assertNotNull(stateAtFetch.get())
                assertEquals(
                    stateStore.currentState.subscriptionGroups,
                    stateAtFetch.get()?.subscriptionGroups,
                )
                assertEquals(0, result.updateResult.updatedGroupCount)
                assertEquals(1, result.updateResult.failedGroupCount)
                assertEquals(null, result.existingGroupName)
            }
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun exact_url_reuses_the_entire_existing_group_and_reports_its_saved_name() = runBlocking {
        val stateStore = cleanStateStore()
        val stateAtFetch = AtomicReference<AppState?>()

        try {
            withFailingSubscriptionServer(
                onRequest = { stateAtFetch.set(stateStore.currentState) },
            ) { url ->
                val existing = SubscriptionGroupState(
                    id = 41,
                    name = "Saved name",
                    url = url,
                    userAgent = "Stored UA",
                    updateInterval = "12",
                    hwid = "stored-hwid",
                    ageSecretKey = "stored-secret",
                    updateViaProxy = true,
                    autoOverrideRules = false,
                    enabled = false,
                    builtIn = false,
                    lastUpdatedAtMillis = 1234L,
                    profileTitle = "Saved title",
                    announce = "Saved announcement",
                    supportUrl = "https://support.example.test/",
                    supportEmail = "support@example.test",
                    profileWebPageUrl = "https://profile.example.test/",
                    announceUrl = "https://announce.example.test/",
                    trafficUploadBytes = 101L,
                    trafficDownloadBytes = 202L,
                    trafficTotalBytes = 303L,
                    trafficExpireAtSeconds = 404L,
                    notifyOnExpiry = false,
                    customExpiryReminders = listOf(SubscriptionExpiryReminder(5, ExpiryReminderUnit.Days)),
                )
                stateStore.update(persist = false) { state ->
                    state.copy(
                        subscriptionGroups = listOf(existing),
                        nextSubscriptionGroupId = 42,
                    )
                }

                val result = SubscriptionInstallConfigUseCase(
                    stateStore = stateStore,
                    subscriptionFetcher = AndroidSubscriptionFetcher("install-test", TestAgeCrypto),
                ).install(
                    SubscriptionInstallConfig(
                        name = "Incoming name",
                        url = url,
                        userAgent = "Incoming UA",
                    ),
                )

                assertEquals(listOf(existing), stateStore.currentState.subscriptionGroups)
                assertEquals(42, stateStore.currentState.nextSubscriptionGroupId)
                assertEquals(listOf(existing), stateAtFetch.get()?.subscriptionGroups)
                assertEquals("Saved name", result.existingGroupName)
                assertEquals(1, result.updateResult.failedGroupCount)
            }
        } finally {
            stateStore.resetToStockState()
        }
    }
}

private val TestAgeCrypto = object : SubscriptionAgeCrypto {
    override fun publicKey(secretKey: String): String = "age1test-recipient"

    override fun decryptArmored(text: String, secretKey: String): String = text
}

private suspend fun cleanStateStore(): AndroidAppStateStore {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return AndroidAppStateStore.get(context).also { store ->
        store.resetToStockState()
    }
}

private suspend fun withFailingSubscriptionServer(
    onRequest: () -> Unit,
    block: suspend (String) -> Unit,
) {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/sub") { exchange ->
        onRequest()
        val body = "temporary failure".encodeToByteArray()
        exchange.sendResponseHeaders(503, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
    server.start()
    try {
        block("http://127.0.0.1:${server.address.port}/sub")
    } finally {
        server.stop(0)
    }
}
