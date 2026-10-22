// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.subscription.SubscriptionAgeCrypto
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.LocalProxyXrayConfigOptions

// Desktop transport binding only: parsing and eligibility belong to the shared executor tests.
class DesktopTrafficConfigProfileFetcherTest {
    @Test
    fun activeRuntimeProxyUsesLoopbackPortAndCredentialsInsteadOfPersistedListenerSettings() = runBlocking {
        val observations = mutableListOf<RecordedRequest>()
        val ageCrypto = RecordingAgeCrypto()
        val fetcher = recordingFetcher(observations, ageCrypto)
        val settings = DesktopAppSettings(
            localProxyPort = 11_111,
            localProxyListenAddress = "0.0.0.0",
            enableLocalProxyAuth = false,
            localProxyUsername = "stale-user",
            localProxyPassword = "stale-password",
            subscriptionFetchTimeoutSeconds = 18,
            subscriptionUserAgent = "Global subscription agent",
            sendDeviceHeaders = false,
        )
        val runningOptions = LocalProxyXrayConfigOptions(
            socksPort = 20_480,
            listenAddress = "0.0.0.0",
            username = "running-user",
            password = "running-password",
        )
        val service = DesktopTrafficConfigProfileFetcher(
            fetcher = fetcher,
            readSettings = { settings },
            activeProxy = { runningOptions },
        )

        assertEquals("fixture body", service.fetch(profile(userAgent = "Profile agent"), profileUrl))

        val request = observations.single()
        assertEquals(Proxy.Type.SOCKS, request.proxy?.type())
        val endpoint = assertNotNull(request.proxy?.address() as? InetSocketAddress)
        assertEquals("127.0.0.1", endpoint.hostString)
        assertEquals(runningOptions.socksPort, endpoint.port)
        assertEquals("running-user" to "running-password", request.socksCredentials)
        assertEquals(18_000, request.connection.connectTimeout)
        assertEquals(18_000, request.connection.readTimeout)
        assertFalse(request.connection.requestHeaders.containsKey("X-Age-Public-Key"))
        assertTrue(ageCrypto.publicKeySecrets.isEmpty())
    }

    @Test
    fun eachFetchReadsCurrentTimeoutDeviceIdentityAndActiveEndpoint() = runBlocking {
        val observations = mutableListOf<RecordedRequest>()
        val settingsSnapshots = mutableListOf<DesktopAppSettings>()
        val runtimeSnapshots = mutableListOf<LocalProxyXrayConfigOptions?>()
        var currentSettings = DesktopAppSettings(
            localProxyPort = 11_111,
            subscriptionFetchTimeoutSeconds = 12,
            subscriptionUserAgent = "Global agent one",
            sendDeviceHeaders = true,
            installationUuid = "installation-one",
        )
        var currentRuntime: LocalProxyXrayConfigOptions? = LocalProxyXrayConfigOptions(
            socksPort = 21_001,
            username = "runtime-one",
            password = "secret-one",
        )
        val service = DesktopTrafficConfigProfileFetcher(
            fetcher = recordingFetcher(observations, RecordingAgeCrypto()),
            readSettings = { currentSettings.also(settingsSnapshots::add) },
            activeProxy = { currentRuntime.also(runtimeSnapshots::add) },
        )

        service.fetch(profile(userAgent = "Profile agent"), profileUrl)
        currentSettings = currentSettings.copy(
            localProxyPort = 12_222,
            subscriptionFetchTimeoutSeconds = 34,
            subscriptionUserAgent = "Global agent two",
            installationUuid = "installation-two",
        )
        currentRuntime = LocalProxyXrayConfigOptions(
            socksPort = 21_002,
            username = "runtime-two",
            password = "secret-two",
        )
        service.fetch(profile(userAgent = "Profile agent"), profileUrl)

        assertEquals(listOf(12, 34), settingsSnapshots.map(DesktopAppSettings::subscriptionFetchTimeoutSeconds))
        assertEquals(listOf(21_001, 21_002), runtimeSnapshots.map { it?.socksPort })
        assertEquals(listOf(12_000, 34_000), observations.map { it.connection.connectTimeout })
        assertEquals(listOf(12_000, 34_000), observations.map { it.connection.readTimeout })
        assertEquals(listOf(21_001, 21_002), observations.map { proxyPort(it.proxy) })
        assertEquals(
            listOf("runtime-one" to "secret-one", "runtime-two" to "secret-two"),
            observations.map { it.socksCredentials },
        )
        val deviceIds = observations.map { it.connection.requestHeaders.getValue("X-Device-ID") }
        assertTrue(deviceIds.all(String::isNotBlank))
        assertNotEquals(deviceIds[0], deviceIds[1], "headers should use each fetch's current installation UUID")
        assertEquals(listOf("Profile agent", "Profile agent"), observations.map { it.connection.requestHeaders["User-Agent"] })
        assertTrue(observations.all { "X-Age-Public-Key" !in it.connection.requestHeaders })
    }

    @Test
    fun profileUserAgentIsPassedAsStoredAndBlankUsesFetcherDefaultNotGlobalSetting() = runBlocking {
        val observations = mutableListOf<RecordedRequest>()
        var settings = DesktopAppSettings(subscriptionUserAgent = "Global fallback one", sendDeviceHeaders = false)
        val versionedProfileFallback = versionedDesktopProfileResourceUserAgent(app.ProjectInfo.VERSION_NAME)
        val service = DesktopTrafficConfigProfileFetcher(
            fetcher = recordingFetcher(observations, RecordingAgeCrypto()),
            readSettings = { settings },
            activeProxy = { null },
        )

        service.fetch(profile(userAgent = "  Profile-specific agent  "), profileUrl)
        settings = settings.copy(subscriptionUserAgent = "Global fallback two")
        service.fetch(profile(userAgent = ""), profileUrl)
        service.fetch(profile(userAgent = versionedProfileFallback), profileUrl)

        assertEquals(
            listOf("  Profile-specific agent  ", DefaultDesktopSubscriptionUserAgent, versionedProfileFallback),
            observations.map { it.connection.requestHeaders["User-Agent"] },
        )
        assertTrue(observations.all { "X-Age-Public-Key" !in it.connection.requestHeaders })
    }

    @Test
    fun absentRunningProxyDoesNotReusePersistedPortOrCredentials() = runBlocking {
        val observations = mutableListOf<RecordedRequest>()
        val settings = DesktopAppSettings(
            localProxyPort = 22_222,
            enableLocalProxyAuth = true,
            localProxyUsername = "saved-user",
            localProxyPassword = "saved-password",
            sendDeviceHeaders = false,
        )
        val service = DesktopTrafficConfigProfileFetcher(
            fetcher = recordingFetcher(observations, RecordingAgeCrypto()),
            readSettings = { settings },
            activeProxy = { null },
        )

        service.fetch(profile(userAgent = "Profile agent"), profileUrl)

        assertNull(observations.single().proxy)
        assertNull(observations.single().socksCredentials)
    }

    @Test
    fun manualRefreshBindingStaysDirectAndSendsCurrentOptInHeadersTimeoutAndProfileAgent() = runBlocking {
        val observations = mutableListOf<RecordedRequest>()
        val ageCrypto = RecordingAgeCrypto()
        val runningCoreEndpoint = LocalProxyXrayConfigOptions(
            socksPort = 20_480,
            username = "running-core-user",
            password = "running-core-password",
        )
        val settings = DesktopAppSettings(
            localProxyPort = 11_111,
            localProxyListenAddress = "0.0.0.0",
            enableLocalProxyAuth = true,
            localProxyUsername = "persisted-user",
            localProxyPassword = "persisted-password",
            subscriptionFetchTimeoutSeconds = 37,
            subscriptionUserAgent = "Global fallback must not win",
            sendDeviceHeaders = true,
            installationUuid = "manual-refresh-test-installation",
        )
        val fetcher = recordingFetcher(observations, ageCrypto)
        val automaticFetcher = DesktopTrafficConfigProfileFetcher(
            fetcher = fetcher,
            readSettings = { settings },
            activeProxy = { runningCoreEndpoint },
        )
        val manualFetcher = DesktopTrafficConfigProfileFetcher(
            fetcher = fetcher,
            readSettings = { settings },
            activeProxy = { null },
        )
        val profile = profile(userAgent = "Manual profile agent/1.0")
        val trimmedUrl = "https://profiles.example.test/manual.conf"

        // Main should own both bindings while Core is running, then pass only this
        // direct-only instance as DesktopConfigsScreen.fetchProfileDocument.
        automaticFetcher.fetch(profile, trimmedUrl)
        manualFetcher.fetch(profile, trimmedUrl)

        assertEquals(runningCoreEndpoint.socksPort, proxyPort(observations[0].proxy))
        val manualRequest = observations[1]
        assertNull(manualRequest.proxy, "Manual refresh remains direct when automatic refresh has a live proxy")
        assertNull(manualRequest.socksCredentials)
        assertEquals(trimmedUrl, manualRequest.connection.url.toString())
        assertEquals(37_000, manualRequest.connection.connectTimeout)
        assertEquals(37_000, manualRequest.connection.readTimeout)
        assertEquals("Manual profile agent/1.0", manualRequest.connection.requestHeaders["User-Agent"])
        assertEquals(
            DesktopDeviceIdentity.deviceHeaders(settings.installationUuid)["X-Device-ID"],
            manualRequest.connection.requestHeaders["X-Device-ID"],
        )
        assertTrue(manualRequest.connection.requestHeaders.containsKey("x-client"))
        assertFalse(manualRequest.connection.requestHeaders.containsKey("X-Age-Public-Key"))
        assertTrue(ageCrypto.publicKeySecrets.isEmpty())
    }

    @Test
    fun deviceHeaderOptOutOmitsIdentityAndBlankAgeKeyAddsNoAgeHeader() = runBlocking {
        val observations = mutableListOf<RecordedRequest>()
        val ageCrypto = RecordingAgeCrypto()
        val settings = DesktopAppSettings(
            sendDeviceHeaders = false,
            installationUuid = "must-not-be-sent",
            subscriptionUserAgent = "Global fallback",
        )
        val service = DesktopTrafficConfigProfileFetcher(
            fetcher = recordingFetcher(observations, ageCrypto),
            readSettings = { settings },
            activeProxy = { null },
        )

        service.fetch(profile(userAgent = "Profile agent"), profileUrl)

        val headers = observations.single().connection.requestHeaders
        assertFalse(headers.keys.any { key -> key.equals("X-Device-ID", ignoreCase = true) })
        assertFalse(headers.keys.any { key -> key.startsWith("x-device", ignoreCase = true) })
        assertFalse(headers.containsKey("X-Age-Public-Key"))
        assertTrue(ageCrypto.publicKeySecrets.isEmpty())
    }

    private fun profile(userAgent: String) = TrafficConfigState(
        id = 9,
        name = "Auto refresh profile",
        rawConfig = "[General]\nloglevel = warning\n",
        resourceSettings = TrafficConfigResourceSettings(userAgent = userAgent),
    )

    private fun recordingFetcher(
        observations: MutableList<RecordedRequest>,
        ageCrypto: SubscriptionAgeCrypto,
    ) = DesktopSubscriptionFetcher(
        urlConnectionFactory = { url, proxy ->
            val endpoint = proxy?.address() as? InetSocketAddress
            val credentials = endpoint?.let {
                Authenticator.requestPasswordAuthentication(
                    it.hostString,
                    InetAddress.getByName(it.hostString),
                    it.port,
                    "SOCKS5",
                    "test",
                    "SOCKS5",
                )?.let { auth -> auth.userName to String(auth.password) }
            }
            RecordingConnection(url).also { connection ->
                observations += RecordedRequest(connection, proxy, credentials)
            }
        },
        ageCrypto = ageCrypto,
    )

    private fun proxyPort(proxy: Proxy?): Int? = (proxy?.address() as? InetSocketAddress)?.port

    private data class RecordedRequest(
        val connection: RecordingConnection,
        val proxy: Proxy?,
        val socksCredentials: Pair<String, String>?,
    )

    private class RecordingConnection(url: URL) : HttpURLConnection(url) {
        private val body = "fixture body".encodeToByteArray()
        val requestHeaders = linkedMapOf<String, String>()

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = true
        override fun getResponseCode(): Int = 200
        override fun getInputStream(): InputStream = ByteArrayInputStream(body)
        override fun getContentLengthLong(): Long = body.size.toLong()
        override fun setRequestProperty(key: String?, value: String?) {
            if (key != null && value != null) requestHeaders[key] = value
        }
    }

    private class RecordingAgeCrypto : SubscriptionAgeCrypto {
        val publicKeySecrets = mutableListOf<String>()

        override fun publicKey(secretKey: String): String {
            publicKeySecrets += secretKey
            return "age1test-recipient"
        }

        override fun decryptArmored(text: String, secretKey: String): String = text
    }

    private companion object {
        const val profileUrl = "https://profile.example/traffic.conf"
    }
}
