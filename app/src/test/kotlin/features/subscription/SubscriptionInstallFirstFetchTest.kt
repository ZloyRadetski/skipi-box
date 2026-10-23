// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.SubscriptionGroupState
import data.AndroidAppStateStore
import features.config.TrafficConfigState
import features.subscription.runtime.AndroidSubscriptionFetchOptions
import features.subscription.runtime.AndroidSubscriptionFetcher
import features.subscription.usecase.toSubscriptionFetchOptions
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SubscriptionInstallFirstFetchTest {
    @Test
    fun disabled_duplicate_uses_its_saved_user_agent_and_age_key_on_the_first_fetch() = runBlocking {
        val stateStore = cleanInstallTestStateStore()
        val capturedRequest = AtomicReference<CapturedSubscriptionRequest?>()
        val existing = SubscriptionGroupState(
            id = 41,
            name = "Saved subscription",
            url = "",
            userAgent = "Stored UA",
            updateInterval = "12",
            hwid = "saved-device",
            ageSecretKey = "saved-age-secret",
            updateViaProxy = true,
            autoOverrideRules = false,
            enabled = false,
            builtIn = false,
            lastUpdatedAtMillis = 1234L,
            notifyOnExpiry = false,
        )

        try {
            withSubscriptionServer { url, requests ->
                val savedGroup = existing.copy(url = url)
                stateStore.update(persist = false) { state ->
                    state.copy(
                        subscriptionGroups = listOf(savedGroup),
                        nextSubscriptionGroupId = 42,
                        proxyRunning = false,
                        enableSubscriptionDeviceHeaders = false,
                        subscriptionFetchTimeoutSeconds = 4,
                    )
                }
                val options = stateStore.currentState.toSubscriptionFetchOptions(savedGroup)
                assertEquals(
                    AndroidSubscriptionFetchOptions(
                        useRunningProxy = false,
                        ageSecretKey = "saved-age-secret",
                        sendDeviceHeaders = false,
                        timeoutSeconds = 4,
                    ),
                    options,
                )

                val result = SubscriptionInstallConfigUseCase(
                    stateStore = stateStore,
                    subscriptionFetcher = AndroidSubscriptionFetcher("install-test", TestAgeCrypto),
                ).install(
                    SubscriptionInstallConfig(
                        name = "Incoming title",
                        url = url,
                        userAgent = "Incoming UA",
                    ),
                )

                capturedRequest.set(requests.single())
                val refreshedGroup = stateStore.currentState.subscriptionGroups.single()
                assertEquals(savedGroup.id, refreshedGroup.id)
                assertEquals(savedGroup.url, refreshedGroup.url)
                assertEquals("Saved subscription", refreshedGroup.name)
                assertEquals("Stored UA", refreshedGroup.userAgent)
                assertEquals("saved-age-secret", refreshedGroup.ageSecretKey)
                assertEquals(false, refreshedGroup.enabled)
                assertEquals(true, refreshedGroup.updateViaProxy)
                assertEquals("Saved subscription", result.existingGroupName)
                assertEquals(1, result.updateResult.updatedGroupCount)
                assertEquals(0, result.updateResult.failedGroupCount)
            }

            val request = checkNotNull(capturedRequest.get())
            assertEquals("Stored UA", request.userAgent)
            assertEquals("age1test-recipient", request.agePublicKey)
            assertNull(request.clientHeader)
            assertFalse(stateStore.currentState.subscriptionGroups.single().enabled)
        } finally {
            stateStore.resetToStockState()
        }
    }

    @Test
    fun new_install_keeps_android_defaults_and_commits_non_activating_embedded_profile() = runBlocking {
        val stateStore = cleanInstallTestStateStore()
        val capturedRequests = AtomicReference<List<CapturedSubscriptionRequest>>(emptyList())
        val profileText = """
            [Proxy Group]
            Imported = select, DIRECT, skipi-display=always
        """.trimIndent()
        val routingPayload = "skipi://conf/add/" +
            Base64.getUrlEncoder().withoutPadding().encodeToString(profileText.encodeToByteArray())
        val existingProfile = TrafficConfigState(
            id = 10,
            name = "Current profile",
            rawConfig = """
                [Proxy Group]
                Current = select, DIRECT
            """.trimIndent(),
            sourceUrl = "https://current.example/profile.conf",
        )

        try {
            stateStore.update(persist = false) { state ->
                state.copy(
                    trafficConfigs = listOf(existingProfile),
                    activeTrafficConfigId = existingProfile.id,
                    nextTrafficConfigId = existingProfile.id + 1,
                    proxyRunning = false,
                    enableSubscriptionDeviceHeaders = true,
                    subscriptionFetchTimeoutSeconds = 10,
                )
            }
            withSubscriptionServer(
                responseHeaders = mapOf("routing" to routingPayload),
            ) { url, requests ->
                val result = SubscriptionInstallConfigUseCase(
                    stateStore = stateStore,
                    subscriptionFetcher = AndroidSubscriptionFetcher("install-test", TestAgeCrypto),
                ).install(
                    SubscriptionInstallConfig(
                        name = "Imported subscription",
                        url = url,
                        userAgent = DefaultSubscriptionUserAgent,
                    ),
                )

                capturedRequests.set(requests.toList())
                val installed = stateStore.currentState.subscriptionGroups.single { group -> group.url == url }
                assertEquals(DefaultSubscriptionUserAgent, installed.userAgent)
                assertEquals("", installed.ageSecretKey)
                assertFalse(installed.updateViaProxy)
                assertTrue(installed.enabled)
                assertEquals(1, result.updateResult.updatedGroupCount)
                assertEquals(0, result.updateResult.failedGroupCount)
            }

            val request = capturedRequests.get().single()
            assertEquals(DefaultSubscriptionUserAgent, request.userAgent)
            assertNull(request.agePublicKey)
            assertEquals("SKIPI", request.clientHeader)

            val updated = stateStore.currentState
            assertEquals(existingProfile.id, updated.activeTrafficConfigId)
            val importedProfile = updated.trafficConfigs.single { profile ->
                profile.sourceUrl.startsWith("subscription://")
            }
            assertTrue(importedProfile.rawConfig.contains("Imported = select, DIRECT"))
            assertEquals("Imported subscription", importedProfile.name)
        } finally {
            stateStore.resetToStockState()
        }
    }
}

private data class CapturedSubscriptionRequest(
    val userAgent: String?,
    val agePublicKey: String?,
    val clientHeader: String?,
)

private val TestAgeCrypto = object : SubscriptionAgeCrypto {
    override fun publicKey(secretKey: String): String = "age1test-recipient"
    override fun decryptArmored(text: String, secretKey: String): String = text
}

private suspend fun cleanInstallTestStateStore(): AndroidAppStateStore {
    val context = ApplicationProvider.getApplicationContext<Context>()
    return AndroidAppStateStore.get(context).also { store -> store.resetToStockState() }
}

private suspend fun withSubscriptionServer(
    responseHeaders: Map<String, String> = emptyMap(),
    block: suspend (url: String, requests: List<CapturedSubscriptionRequest>) -> Unit,
) {
    val requests = CopyOnWriteArrayList<CapturedSubscriptionRequest>()
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/subscription") { exchange ->
        requests += CapturedSubscriptionRequest(
            userAgent = exchange.requestHeaders.getFirst("User-Agent"),
            agePublicKey = exchange.requestHeaders.getFirst("X-Age-Public-Key"),
            clientHeader = exchange.requestHeaders.getFirst("x-client"),
        )
        responseHeaders.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
        val body = "vless://4219d973-8792-462f-8747-df766f70f137@node.example:443?security=tls&type=tcp#Node"
            .encodeToByteArray()
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { output -> output.write(body) }
    }
    server.start()
    try {
        block("http://127.0.0.1:${server.address.port}/subscription", requests)
    } finally {
        server.stop(0)
    }
}
