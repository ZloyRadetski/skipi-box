// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import platform.LocalProxyAuthMode
import platform.LocalProxyCredentials
import platform.LocalProxyJsonAuth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The HTTP inbound is a setting of its own on Desktop; the system proxy is only one user of it. */
class DesktopLocalHttpProxyTest {
    @Test
    fun theHttpInboundIsOffByDefaultWithoutAuthentication() {
        val settings = DesktopAppSettings(useSystemProxy = false, useWindowsSystemProxy = false)

        assertFalse(settings.enableLocalHttpProxy)
        assertEquals(LocalProxyAuthMode.Off, settings.httpAuthMode())
        assertEquals(LocalProxyAuthMode.Off, settings.socksAuthMode(), "Desktop never authenticated SOCKS by default")
    }

    @Test
    fun switchingTheHttpInboundOffAlsoSwitchesTheSystemProxyOff() {
        val on = DesktopAppSettings().withSystemProxy(enabled = true, supported = true)
        assertTrue(on.enableLocalHttpProxy, "the system proxy needs the inbound")

        val off = on.withHttpInbound(false)

        assertFalse(off.enableLocalHttpProxy)
        assertFalse(off.useSystemProxy || off.useWindowsSystemProxy)
    }

    @Test
    fun theHttpInboundCanRunWithoutTheSystemProxy() {
        val settings = DesktopAppSettings().withSystemProxy(enabled = false, supported = true).withHttpInbound(true)

        assertTrue(settings.enableLocalHttpProxy)
        assertFalse(settings.useSystemProxy)
    }

    @Test
    fun anOldSettingsFileWithTheSystemProxyOnKeepsItsHttpInbound() {
        val old = DesktopAppSettings(useSystemProxy = true, useWindowsSystemProxy = true, enableLocalHttpProxy = false)

        val normalized = old.normalized(supportsSystemProxy = true, supportsWindowsSystemProxy = true)

        assertTrue(normalized.enableLocalHttpProxy)
    }

    @Test
    fun anAuthModeSetsItsSwitchAndKeepsTheLastRealModeWhileOff() {
        val manual = DesktopAppSettings().withSocksAuthMode(LocalProxyAuthMode.Manual)
        assertEquals(LocalProxyAuthMode.Manual, manual.socksAuthMode())

        val off = manual.withSocksAuthMode(LocalProxyAuthMode.Off)
        assertEquals(LocalProxyAuthMode.Off, off.socksAuthMode())
        assertEquals(LocalProxyAuthMode.Manual, off.withSocksAuthMode(LocalProxyAuthMode.Manual).socksAuthMode())

        val http = DesktopAppSettings().withHttpAuthMode(LocalProxyAuthMode.FromJson)
        assertEquals(LocalProxyAuthMode.FromJson, http.httpAuthMode())
        assertEquals(LocalProxyAuthMode.Off, http.socksAuthMode(), "the two inbounds never share a mode")
    }

    @Test
    fun aModeThatNeedsALoginGeneratesOneWhenNoneIsStored() {
        val auto = DesktopAppSettings(localProxyUsername = "", localProxyPassword = "")
            .withSocksAuthMode(LocalProxyAuthMode.Auto)
        assertTrue(auto.localProxyUsername.isNotBlank() && auto.localProxyPassword.isNotBlank())

        val http = DesktopAppSettings().withHttpAuthMode(LocalProxyAuthMode.Manual)
        assertTrue(http.localHttpProxyUsername.isNotBlank() && http.localHttpProxyPassword.isNotBlank())
        assertNotEquals(auto.localProxyUsername, http.localHttpProxyUsername)
    }

    @Test
    fun aStoredLoginIsNeverReplacedByChoosingAMode() {
        val settings = DesktopAppSettings(localProxyUsername = "mine", localProxyPassword = "secret")
            .withSocksAuthMode(LocalProxyAuthMode.Auto)

        assertEquals("mine", settings.localProxyUsername)
        assertEquals("secret", settings.localProxyPassword)
    }

    @Test
    fun offAndFromJsonNeverGenerateALogin() {
        val off = DesktopAppSettings().withSocksAuthMode(LocalProxyAuthMode.Off)
        val json = DesktopAppSettings().withHttpAuthMode(LocalProxyAuthMode.FromJson)

        assertEquals("", off.localProxyUsername)
        assertEquals("", json.localHttpProxyUsername)
    }

    @Test
    fun generatingReplacesOnlyTheLoginOfThatInbound() {
        val settings = DesktopAppSettings(
            localProxyUsername = "s", localProxyPassword = "sp",
            localHttpProxyUsername = "h", localHttpProxyPassword = "hp",
        )

        val socks = settings.withGeneratedSocksCredentials()
        assertNotEquals("s", socks.localProxyUsername)
        assertEquals("h", socks.localHttpProxyUsername)

        val http = settings.withGeneratedHttpCredentials()
        assertNotEquals("h", http.localHttpProxyUsername)
        assertEquals("s", http.localProxyUsername)
    }

    @Test
    fun typedCredentialsAreStoredPerInbound() {
        val settings = DesktopAppSettings()
            .withSocksCredentials("su", "sp")
            .withHttpCredentials("hu", "hp")

        assertEquals("su" to "sp", settings.localProxyUsername to settings.localProxyPassword)
        assertEquals("hu" to "hp", settings.localHttpProxyUsername to settings.localHttpProxyPassword)
    }

    @Test
    fun theHttpInboundOfTheCoreFollowsTheSettingNotOnlyTheSystemProxy() {
        val inboundOnly = DesktopAppSettings(
            useSystemProxy = false, useWindowsSystemProxy = false, enableLocalHttpProxy = true, localHttpProxyPort = 18_081,
        ).toLocalProxyXrayConfigOptions()

        assertTrue(inboundOnly.enableHttpProxy)
        assertEquals(18_081, inboundOnly.httpProxyPort)
    }

    @Test
    fun noHttpInboundMeansNoHttpListener() {
        val options = DesktopAppSettings(useSystemProxy = false, useWindowsSystemProxy = false).toLocalProxyXrayConfigOptions()

        assertFalse(options.enableHttpProxy)
    }

    @Test
    fun theHttpInboundListensLikeSocks() {
        val lan = DesktopAppSettings(enableLocalHttpProxy = true, localProxyListenAddress = "0.0.0.0").toLocalProxyXrayConfigOptions()
        val local = DesktopAppSettings(enableLocalHttpProxy = true).toLocalProxyXrayConfigOptions()

        assertEquals("0.0.0.0", lan.httpProxyListenAddress)
        assertEquals("127.0.0.1", local.httpProxyListenAddress)
    }

    @Test
    fun eachInboundGetsItsOwnResolvedLogin() {
        val settings = DesktopAppSettings(
            enableLocalHttpProxy = true,
            localProxyUsername = "s-user", localProxyPassword = "s-pass",
            localHttpProxyUsername = "h-user", localHttpProxyPassword = "h-pass",
        ).withSocksAuthMode(LocalProxyAuthMode.Manual).withHttpAuthMode(LocalProxyAuthMode.Manual)

        val options = settings.toLocalProxyXrayConfigOptions()

        assertEquals("s-user" to "s-pass", options.username to options.password)
        assertEquals("h-user" to "h-pass", options.httpUsername to options.httpPassword)
    }

    @Test
    fun autoPrefersTheLoginOfTheCustomJsonAndFromJsonNeedsOne() {
        val json = LocalProxyJsonAuth(
            socks = LocalProxyCredentials("json-s", "json-sp"),
            http = LocalProxyCredentials("json-h", "json-hp"),
        )
        val auto = DesktopAppSettings(enableLocalHttpProxy = true, localProxyUsername = "stored", localProxyPassword = "p")
            .withSocksAuthMode(LocalProxyAuthMode.Auto)
            .withHttpAuthMode(LocalProxyAuthMode.FromJson)

        val withJson = auto.toLocalProxyXrayConfigOptions(jsonAuth = json)
        assertEquals("json-s", withJson.username)
        assertEquals("json-h", withJson.httpUsername)

        val withoutJson = auto.toLocalProxyXrayConfigOptions()
        assertEquals("stored", withoutJson.username)
        assertEquals("", withoutJson.httpUsername, "from JSON without a login in the JSON leaves HTTP open")
    }

    @Test
    fun theSettingsStateShowsTheHttpInboundItsPortAndBothModes() {
        val settings = DesktopAppSettings(
            enableLocalHttpProxy = true,
            localHttpProxyPort = 18_081,
            localHttpProxyUsername = "h-user",
            localHttpProxyPassword = "h-pass",
        ).withSocksAuthMode(LocalProxyAuthMode.FromJson).withHttpAuthMode(LocalProxyAuthMode.Manual)

        val state = settings.toLocalProxySettingsState()

        assertTrue(state.enableHttpEndpoint)
        assertEquals("18081", state.httpPort)
        assertEquals(LocalProxyAuthMode.FromJson, state.socksAuthMode)
        assertEquals(LocalProxyAuthMode.Manual, state.httpAuthMode)
        assertEquals("h-user", state.httpUsername)
        assertEquals("h-pass", state.httpPassword)
    }

    @Test
    fun changingTheHttpInboundOrAnyAuthSettingRestartsCore() {
        val base = DesktopAppSettings()

        listOf(
            base.copy(enableLocalHttpProxy = !base.enableLocalHttpProxy),
            base.withSocksAuthMode(LocalProxyAuthMode.Manual),
            base.withHttpAuthMode(LocalProxyAuthMode.Manual),
            base.copy(localHttpProxyUsername = "changed"),
            base.copy(localHttpProxyPassword = "changed"),
        ).forEach { changed ->
            assertTrue(desktopSettingsNeedReconnect(base, changed), "$changed")
        }
    }
}
