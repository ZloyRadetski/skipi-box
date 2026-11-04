// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import app.withLocalProxyHttpAuthMode
import app.withLocalProxySocksAuthMode
import engine.vpn.toVpnAppendHttpProxyOptions
import platform.LocalProxyAuthMode
import platform.LocalProxyCredentials
import platform.LocalProxyJsonAuth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocalProxyHttpOptionsTest {
    @Test
    fun dynamicPortAlsoMovesTheHttpPortWhenTheHttpInboundIsOn() {
        val resolved = AppState(
            localProxyPort = "1",
            localHttpProxyPort = "2",
            enableDynamicLocalProxyPort = true,
            enableLocalHttpProxy = true,
        ).withResolvedDynamicLocalProxyPort()

        val socks = resolved.localProxyPort.toInt()
        val http = resolved.localHttpProxyPort.toInt()
        assertTrue(http in 1..65_535)
        assertNotEquals(2, http)
        assertNotEquals(socks, http, "the two inbounds never share a port")
    }

    @Test
    fun dynamicPortLeavesTheHttpPortAloneWhileTheHttpInboundIsOff() {
        val resolved = AppState(localHttpProxyPort = "2", enableDynamicLocalProxyPort = true)
            .withResolvedDynamicLocalProxyPort()

        assertEquals("2", resolved.localHttpProxyPort)
    }

    @Test
    fun theVpnProxyAlsoCountsAsAnEnabledHttpInboundForDynamicPorts() {
        val resolved = AppState(localHttpProxyPort = "2", enableDynamicLocalProxyPort = true, enableVpnAppendHttpProxy = true)
            .withResolvedDynamicLocalProxyPort()

        assertNotEquals("2", resolved.localHttpProxyPort)
    }

    @Test
    fun withoutDynamicPortsTheConfiguredPortsStay() {
        val state = AppState(localProxyPort = "21000", localHttpProxyPort = "21001", enableLocalHttpProxy = true)

        assertEquals(state, state.withResolvedDynamicLocalProxyPort())
    }

    @Test
    fun dynamicHttpPortSkipsExcludedPorts() {
        repeat(20) {
            val resolved = AppState(enableDynamicLocalProxyPort = true, enableLocalHttpProxy = true)
                .withResolvedDynamicLocalProxyPort(excludedPorts = setOf(20_003, 20_004))

            assertFalse(resolved.localHttpProxyPort.toInt() in setOf(20_003, 20_004))
        }
    }

    @Test
    fun socksLoginFollowsTheSocksAuthMode() {
        val state = AppState(localProxyUsername = " user ", localProxyPassword = "secret")
        val json = LocalProxyJsonAuth(socks = LocalProxyCredentials("json-user", "json-pass"))

        assertEquals("", state.withLocalProxySocksAuthMode(LocalProxyAuthMode.Off).toLocalProxyOptions().username)
        assertEquals("user", state.withLocalProxySocksAuthMode(LocalProxyAuthMode.Manual).toLocalProxyOptions(json).username)
        assertEquals("json-user", state.withLocalProxySocksAuthMode(LocalProxyAuthMode.Auto).toLocalProxyOptions(json).username)
        assertEquals("user", state.withLocalProxySocksAuthMode(LocalProxyAuthMode.Auto).toLocalProxyOptions().username)
        assertEquals("", state.withLocalProxySocksAuthMode(LocalProxyAuthMode.FromJson).toLocalProxyOptions().username)
    }

    @Test
    fun httpLoginFollowsTheHttpAuthModeAndNeverTheSocksOne() {
        val state = AppState(
            localProxyUsername = "s-user",
            localProxyPassword = "s-pass",
            localHttpProxyUsername = "h-user",
            localHttpProxyPassword = "h-pass",
        ).withLocalProxySocksAuthMode(LocalProxyAuthMode.Manual)

        val off = state.toLocalProxyOptions()
        assertEquals("s-user", off.username)
        assertEquals("", off.httpUsername, "HTTP auth is off by default")

        val manual = state.withLocalProxyHttpAuthMode(LocalProxyAuthMode.Manual).toLocalProxyOptions()
        assertEquals("h-user", manual.httpUsername)
        assertEquals("h-pass", manual.httpPassword)

        val fromJson = state.withLocalProxyHttpAuthMode(LocalProxyAuthMode.FromJson)
            .toLocalProxyOptions(LocalProxyJsonAuth(http = LocalProxyCredentials("j", "p")))
        assertEquals("j", fromJson.httpUsername)
    }

    @Test
    fun theOptionsCarryTheConfiguredHttpPort() {
        val options = AppState(localHttpProxyPort = "21001", enableLocalHttpProxy = true).toLocalProxyOptions()

        assertEquals(21_001, options.httpPort)
    }

    @Test
    fun theInboundIsEnabledWithoutTheVpnProxyAndOnlyAppliedToTheVpnWhenAsked() {
        val local = AppState(localHttpProxyPort = "21001", enableLocalHttpProxy = true)
        val localOptions = local.toLocalProxyOptions()

        val inboundOnly = local.toVpnAppendHttpProxyOptions(localOptions)
        assertTrue(inboundOnly.enabled)
        assertFalse(inboundOnly.appliesToVpn)
        assertEquals(21_001, inboundOnly.port)

        val applied = local.copy(enableVpnAppendHttpProxy = true).toVpnAppendHttpProxyOptions(localOptions)
        assertTrue(applied.enabled)
        assertTrue(applied.appliesToVpn)
    }

    @Test
    fun noHttpInboundMeansNothingIsAppended() {
        val options = AppState().toVpnAppendHttpProxyOptions(AppState().toLocalProxyOptions())

        assertFalse(options.enabled)
        assertFalse(options.appliesToVpn)
    }

    @Test
    fun aConfiguredHttpPortThatCollidesIsReplacedByAFreeOne() {
        val state = AppState(localProxyPort = "21000", localHttpProxyPort = "21000", enableLocalHttpProxy = true)
        val local = state.toLocalProxyOptions()

        assertNotEquals(local.port, state.toVpnAppendHttpProxyOptions(local).port)

        val excluded = AppState(localHttpProxyPort = "21002", enableLocalHttpProxy = true)
        assertNotEquals(21_002, excluded.toVpnAppendHttpProxyOptions(excluded.toLocalProxyOptions(), setOf(21_002)).port)
    }

    @Test
    fun theHttpInboundCarriesItsOwnLoginNotTheSocksLogin() {
        val state = AppState(
            localProxyUsername = "s-user",
            localHttpProxyUsername = "h-user",
            localHttpProxyPassword = "h-pass",
            enableLocalHttpProxy = true,
        ).withLocalProxyHttpAuthMode(LocalProxyAuthMode.Manual)

        val options = state.toVpnAppendHttpProxyOptions(state.toLocalProxyOptions())

        assertEquals("h-user", options.username)
        assertEquals("h-pass", options.password)
    }
}
