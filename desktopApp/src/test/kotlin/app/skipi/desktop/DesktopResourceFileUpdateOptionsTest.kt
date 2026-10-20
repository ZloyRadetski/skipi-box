// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.resources.ResourceFileUpdateOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.LocalProxyXrayConfigOptions

class DesktopResourceFileUpdateOptionsTest {

    @Test
    fun settingsCaptureRawFallbackCredentialsAndTheProfileUserAgent() {
        val settings = DesktopAppSettings(
            localProxyPort = 1080,
            localProxyPortRaw = " 8123 ",
            localProxyUsername = " raw fallback user ",
            localProxyPassword = " raw fallback password ",
            enableLocalProxyAuth = false,
            subscriptionUserAgent = "global subscription fallback",
        )
        val profileUserAgent = " \tProfile resource agent \n"

        val options = settings.resourceFileUpdateOptions(
            userAgent = profileUserAgent,
            proxyRunning = true,
        )

        assertTrue(options.useRunningProxy)
        assertEquals(8123, options.fallbackProxyPort)
        assertEquals(" raw fallback user ", options.fallbackProxyUsername)
        assertEquals(" raw fallback password ", options.fallbackProxyPassword)
        assertEquals(profileUserAgent, options.userAgent)
        assertFalse(options.showNotifications)

        val explicitBlankAgent = settings.resourceFileUpdateOptions(userAgent = "", proxyRunning = false)
        assertFalse(explicitBlankAgent.useRunningProxy)
        assertEquals("", explicitBlankAgent.userAgent)
        assertEquals(8123, explicitBlankAgent.fallbackProxyPort)
        assertFalse(explicitBlankAgent.showNotifications)

        val invalidRawPort = settings.copy(localProxyPortRaw = "not a port")
            .resourceFileUpdateOptions(userAgent = profileUserAgent, proxyRunning = true)
        assertNull(invalidRawPort.fallbackProxyPort)
    }

    @Test
    fun disabledProxyOptionDoesNotCreateAProxyEvenWhenRuntimeEndpointExists() {
        val options = ResourceFileUpdateOptions(useRunningProxy = false)
        val activeRuntime = LocalProxyXrayConfigOptions(socksPort = 19_230)

        assertNull(resolveDesktopResourceFileDownloadProxy(options, activeRuntime))
    }

    @Test
    fun activeRuntimeEndpointOverridesFallbackAndAlwaysUsesLoopback() {
        val options = ResourceFileUpdateOptions(
            useRunningProxy = true,
            fallbackProxyPort = 19_231,
            fallbackProxyUsername = "fallback user",
            fallbackProxyPassword = "fallback password",
        )
        val activeRuntime = LocalProxyXrayConfigOptions(
            socksPort = 19_232,
            listenAddress = "0.0.0.0",
            username = "",
            password = "",
        )

        val proxy = requireNotNull(resolveDesktopResourceFileDownloadProxy(options, activeRuntime))

        assertEquals("127.0.0.1", proxy.host)
        assertEquals(19_232, proxy.port)
        assertEquals("", proxy.username)
        assertEquals("", proxy.password)
    }

    @Test
    fun capturedFallbackIsUsedWhenNoRuntimeEndpointExists() {
        val options = ResourceFileUpdateOptions(
            useRunningProxy = true,
            fallbackProxyPort = 19_233,
            fallbackProxyUsername = "captured user",
            fallbackProxyPassword = "captured password",
        )

        val proxy = requireNotNull(resolveDesktopResourceFileDownloadProxy(options, null))

        assertEquals("127.0.0.1", proxy.host)
        assertEquals(19_233, proxy.port)
        assertEquals("captured user", proxy.username)
        assertEquals("captured password", proxy.password)
    }

    @Test
    fun missingOrInvalidFallbackPortKeepsTheExistingError() {
        val options = ResourceFileUpdateOptions(useRunningProxy = true, fallbackProxyPort = null)

        val error = assertFailsWith<IllegalStateException> {
            resolveDesktopResourceFileDownloadProxy(options, null)
        }
        val invalidPortError = assertFailsWith<IllegalStateException> {
            resolveDesktopResourceFileDownloadProxy(options.copy(fallbackProxyPort = 0), null)
        }

        assertEquals("Local proxy port is unavailable", error.message)
        assertEquals("Local proxy port is unavailable", invalidPortError.message)
    }
}
