// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Android applies each local-proxy change as it is made: a valid port is stored with its text, the
 * "listen on all interfaces" switch is the only way to choose the bind address.
 */
class DesktopLocalProxySettingsEditsTest {
    @Test
    fun validPortIsStoredTogetherWithItsRawText() {
        val next = assertNotNullSettings(DesktopAppSettings().withLocalProxyPort("8080"))

        assertEquals(8080, next.localProxyPort)
        assertEquals("8080", next.localProxyPortRaw)
    }

    @Test
    fun invalidPortIsRejectedAndSettingsStayAsTheyWere() {
        listOf("", "0", "65536", "99999").forEach { text ->
            assertNull(DesktopAppSettings().withLocalProxyPort(text), "port '$text'")
        }
    }

    @Test
    fun socksPortCannotTakeThePortOfTheHttpProxy() {
        val settings = DesktopAppSettings(localProxyPort = 1080, localHttpProxyPort = 8118)

        assertNull(settings.withLocalProxyPort("8118"))
        assertEquals(8119, assertNotNullSettings(settings.withLocalProxyPort("8119")).localProxyPort)
    }

    @Test
    fun listenAllSwitchBindsTheWildcardOrLoopbackAddress() {
        val all = DesktopAppSettings().withListenAllInterfaces(true)
        val loopback = all.withListenAllInterfaces(false)

        assertEquals("0.0.0.0", all.localProxyListenAddress)
        assertEquals("127.0.0.1", loopback.localProxyListenAddress)
    }

    @Test
    fun httpPortMustBeValidAndDifferFromTheSocksPort() {
        val settings = DesktopAppSettings(localProxyPort = 1080, localHttpProxyPort = 10_810)

        assertEquals(8118, assertNotNullSettings(settings.withLocalHttpProxyPort("8118")).localHttpProxyPort)
        assertNull(settings.withLocalHttpProxyPort("1080"))
        assertNull(settings.withLocalHttpProxyPort("0"))
        assertNull(settings.withLocalHttpProxyPort(""))
    }

    @Test
    fun systemProxySwitchSetsBothFlagsOnlyWhereTheOsSupportsIt() {
        val on = DesktopAppSettings().withSystemProxy(enabled = true, supported = true)
        val unsupported = DesktopAppSettings().withSystemProxy(enabled = true, supported = false)

        assertTrue(on.useSystemProxy && on.useWindowsSystemProxy)
        assertFalse(unsupported.useSystemProxy || unsupported.useWindowsSystemProxy)
    }

    private fun assertNotNullSettings(settings: DesktopAppSettings?): DesktopAppSettings =
        settings ?: error("expected the edit to be accepted")
}
