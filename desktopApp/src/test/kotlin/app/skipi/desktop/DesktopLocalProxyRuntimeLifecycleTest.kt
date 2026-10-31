// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionRecord
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.DefaultLocalSocksPort
import platform.LocalProxyXrayConfigOptions

class DesktopLocalProxyRuntimeLifecycleTest {
    @Test
    fun dynamicPortIsAllocatedPerPreparedStartAndAvoidsAnEnabledHttpInbound() {
        val runtime = DesktopLocalProxyRuntime()
        val settings = DesktopAppSettings(
            localProxyPort = 10_808,
            localHttpProxyPort = 10_809,
            useSystemProxy = true,
            enableDynamicLocalProxyPort = true,
            localProxyListenAddress = "0.0.0.0",
            enableLocalProxyAuth = true,
            localProxyUsername = " desktop-user ",
            localProxyPassword = " desktop-secret ",
        )
        val requests = mutableListOf<Pair<String, Set<Int>>>()
        val allocatedPorts = ArrayDeque(listOf(20_480, 20_481))
        val allocate: (String, Set<Int>) -> Int? = { address, excludedPorts ->
            requests += address to excludedPorts
            allocatedPorts.removeFirst()
        }

        val first = runtime.prepareStart(settings, allocatePort = allocate)
        runtime.onCoreStarted(Result.success(DesktopCoreState(isRunning = true)))
        runtime.onCoreStopped(Result.success(DesktopCoreState(isRunning = false)))
        val second = runtime.prepareStart(settings, allocatePort = allocate)

        assertEquals(listOf(20_480, 20_481), listOf(first.socksPort, second.socksPort))
        assertEquals(2, requests.size)
        assertEquals("0.0.0.0", requests.first().first)
        assertEquals(
            if (first.enableHttpProxy) setOf(settings.localHttpProxyPort) else emptySet(),
            requests.first().second,
        )
        assertEquals("desktop-user", first.username)
        assertEquals(" desktop-secret ", first.password)
        assertEquals(settings.localHttpProxyPort, first.httpProxyPort)
        assertEquals(first.enableHttpProxy, second.enableHttpProxy)
        assertEquals(requests.first().second, requests.last().second)
    }

    @Test
    fun staticPortUsesPersistedPortWithoutCallingAllocator() {
        val runtime = DesktopLocalProxyRuntime()
        val settings = DesktopAppSettings(
            localProxyPort = 12_345,
            enableDynamicLocalProxyPort = false,
            enableLocalProxyAuth = true,
            localProxyUsername = " static-user ",
            localProxyPassword = "static-secret",
        )
        var allocatorCalled = false

        val options = runtime.prepareStart(settings) { _, _ ->
            allocatorCalled = true
            20_480
        }

        assertEquals(12_345, options.socksPort)
        assertEquals("static-user", options.username)
        assertEquals("static-secret", options.password)
        assertFalse(allocatorCalled)
    }

    @Test
    fun invalidRawStaticPortUsesAndroidDefaultRatherThanAStaleParsedDesktopPort() {
        val runtime = DesktopLocalProxyRuntime()
        val settings = DesktopAppSettings(
            localProxyPort = 12_345,
            localProxyPortRaw = "invalid-port",
            enableDynamicLocalProxyPort = false,
        )

        val options = runtime.prepareStart(settings)

        assertEquals(DefaultLocalSocksPort, options.socksPort)
    }

    @Test
    fun failedDynamicAllocationDoesNotFallBackToPersistedPort() {
        val runtime = DesktopLocalProxyRuntime()
        val settings = DesktopAppSettings(
            localProxyPort = 12_345,
            enableDynamicLocalProxyPort = true,
        )

        val error = assertFailsWith<IllegalStateException> {
            runtime.prepareStart(settings) { _, _ -> null }
        }

        assertEquals("Unable to allocate a local proxy port", error.message)
        assertNull(runtime.subscriptionFetchOptions(settings, subscription(updateViaProxy = true), coreRunning = true).proxy)
    }

    @Test
    fun subscriptionRefreshUsesTheLastSuccessfulRuntimeEndpointAndCredentials() {
        val runtime = DesktopLocalProxyRuntime()
        val runningSettings = DesktopAppSettings(
            localProxyPort = 10_808,
            enableDynamicLocalProxyPort = true,
            enableLocalProxyAuth = true,
            localProxyUsername = " proxy-user ",
            localProxyPassword = " proxy-secret ",
        )
        val startOptions = runtime.prepareStart(runningSettings) { _, _ -> 20_480 }
        runtime.onCoreStarted(Result.success(DesktopCoreState(isRunning = true)))

        val currentSettings = runningSettings.copy(
            localProxyPort = 31_337,
            enableDynamicLocalProxyPort = false,
            enableLocalProxyAuth = false,
            localProxyUsername = "new-user",
            localProxyPassword = "new-secret",
            subscriptionFetchTimeoutSeconds = 47,
            sendDeviceHeaders = true,
            installationUuid = "install-uuid",
        )
        val options = runtime.subscriptionFetchOptions(
            settings = currentSettings,
            subscription = subscription(updateViaProxy = true, ageSecretKey = " age-key "),
            coreRunning = true,
        )

        assertEquals(startOptions.socksPort, options.proxy?.port)
        assertEquals(
            DesktopSubscriptionSocksProxy(
                host = "127.0.0.1",
                port = 20_480,
                username = "proxy-user",
                password = " proxy-secret ",
            ),
            options.proxy,
        )
        assertEquals(Duration.ofSeconds(47), options.timeout)
        assertEquals(DesktopDeviceIdentity.deviceHeaders("install-uuid"), options.deviceHeaders)
        assertEquals(" age-key ", options.ageSecretKey)
        assertEquals(features.subscription.DefaultSubscriptionUserAgent, options.fallbackUserAgent)
    }

    @Test
    fun failedOrNonRunningCoreStartDoesNotPublishThePreparedEndpoint() {
        val settings = DesktopAppSettings(enableDynamicLocalProxyPort = true)
        val failedStart = DesktopLocalProxyRuntime().also { runtime ->
            runtime.prepareStart(settings) { _, _ -> 20_480 }
            runtime.onCoreStarted(Result.failure(IllegalStateException("start failed")))
            // A later callback without a new prepare must not promote stale pending options.
            runtime.onCoreStarted(Result.success(DesktopCoreState(isRunning = true)))
        }
        val nonRunningStart = DesktopLocalProxyRuntime().also { runtime ->
            runtime.prepareStart(settings) { _, _ -> 20_481 }
            runtime.onCoreStarted(Result.success(DesktopCoreState(isRunning = false)))
        }
        val group = subscription(updateViaProxy = true)

        assertNull(failedStart.subscriptionFetchOptions(settings, group, coreRunning = true).proxy)
        assertNull(nonRunningStart.subscriptionFetchOptions(settings, group, coreRunning = true).proxy)
    }

    @Test
    fun successfulStopClearsTheEndpointWhileFailedStopKeepsTheStillRunningEndpoint() {
        val settings = DesktopAppSettings(enableDynamicLocalProxyPort = true)
        val group = subscription(updateViaProxy = true)
        val afterFailedStop = DesktopLocalProxyRuntime().also { runtime ->
            runtime.prepareStart(settings) { _, _ -> 20_480 }
            runtime.onCoreStarted(Result.success(DesktopCoreState(isRunning = true)))
            runtime.onCoreStopped(Result.failure(IllegalStateException("stop failed")))
        }
        val afterSuccessfulStop = DesktopLocalProxyRuntime().also { runtime ->
            runtime.prepareStart(settings) { _, _ -> 20_481 }
            runtime.onCoreStarted(Result.success(DesktopCoreState(isRunning = true)))
            runtime.onCoreStopped(Result.success(DesktopCoreState(isRunning = false)))
        }

        assertEquals(
            20_480,
            afterFailedStop.subscriptionFetchOptions(settings, group, coreRunning = true).proxy?.port,
        )
        assertNull(afterSuccessfulStop.subscriptionFetchOptions(settings, group, coreRunning = true).proxy)
    }

    @Test
    fun subscriptionMustRequestProxyAndCoreMustStillBeRunning() {
        val runtime = DesktopLocalProxyRuntime()
        val settings = DesktopAppSettings(enableDynamicLocalProxyPort = true)
        runtime.prepareStart(settings) { _, _ -> 20_480 }
        runtime.onCoreStarted(Result.success(DesktopCoreState(isRunning = true)))

        assertNull(
            runtime.subscriptionFetchOptions(settings, subscription(updateViaProxy = false), coreRunning = true).proxy,
        )
        assertNull(
            runtime.subscriptionFetchOptions(settings, subscription(updateViaProxy = true), coreRunning = false).proxy,
        )
    }

    private fun subscription(
        updateViaProxy: Boolean,
        ageSecretKey: String = "",
    ) = SubscriptionRecord(
        id = 8,
        title = "Subscription",
        url = "https://sub.example/list",
        userAgent = "",
        updateInterval = "1d",
        hwid = "install-uuid",
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = false,
        builtIn = false,
        notifyOnExpiry = false,
        customExpiryReminders = null,
    )
}
