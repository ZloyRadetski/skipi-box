// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.server

import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.OlcRtc
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Shadowsocks
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.Trojan
import features.proxy.server.model.V2RayParameters
import features.proxy.server.model.VLESS
import features.proxy.server.model.VMess
import features.proxy.server.model.Wireguard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertIs
import kotlin.test.assertNull

class ProxyServerDraftFactoryTest {
    @Test
    fun createProxyServerEditorDraftUsesAndroidGroupForEveryServerKind() {
        val androidManualGroupId = 1
        val cases = ProxyHomeServerKind.entries.map { kind ->
            kind to if (kind == ProxyHomeServerKind.StrategyGroup) {
                DefaultAutoBalancerGroupId
            } else {
                androidManualGroupId
            }
        }

        cases.forEach { (kind, expectedGroupId) ->
            val result: ProxyServerEditResult = createProxyServerEditorDraft(
                kind = kind,
                defaultGroupId = androidManualGroupId,
            )

            assertNull(result.serverId, kind.name)
            assertEquals(createProxyServerDraft(kind), result.server, kind.name)
            assertEquals<Int?>(expectedGroupId, result.groupId, kind.name)
            assertEquals<Int?>(expectedGroupId, result.returnGroupId, kind.name)
        }
    }

    @Test
    fun createProxyServerDraftMatchesAndroidManualActionDefaults() {
        val cases = listOf(
            ProxyHomeServerKind.Http to HTTP(
                remarks = "",
                server = "",
                port = "",
                user = null,
                password = null,
            ),
            ProxyHomeServerKind.Vmess to VMess(
                remarks = "",
                id = "",
                server = "",
                port = "",
                encryption = "auto",
                parms = defaultV2RayParameters(),
            ),
            ProxyHomeServerKind.Vless to VLESS(
                remarks = "",
                id = "",
                server = "",
                port = "",
                encryption = "",
                flow = "",
                parms = defaultV2RayParameters(),
            ),
            ProxyHomeServerKind.Trojan to Trojan(
                remarks = "",
                password = "",
                server = "",
                port = "",
                parms = defaultV2RayParameters(),
            ),
            ProxyHomeServerKind.Shadowsocks to Shadowsocks(
                remarks = "",
                server = "",
                port = "",
                method = "aes-256-gcm",
                password = "",
                parms = defaultV2RayParameters(),
            ),
            ProxyHomeServerKind.Socks to Socks(
                remarks = "",
                server = "",
                port = "",
                user = null,
                password = null,
            ),
            ProxyHomeServerKind.Hysteria2 to Hysteria2(
                remarks = "",
                server = "",
                port = "",
                auth = "",
                obfs = "",
                obfsPassword = "",
                sni = "",
                pinSHA256 = "",
                mport = "",
                mportHopInt = "",
                up = "",
                down = "",
                security = "none",
            ),
            ProxyHomeServerKind.Wireguard to Wireguard(
                remarks = "",
                server = "",
                port = "",
                secretKey = "",
                publicKey = "",
                preSharedKey = "",
                reserved = "",
                address = "",
                mtu = "",
                finalMask = "",
            ),
            ProxyHomeServerKind.AmneziaWg to AmneziaWg(
                remarks = "",
                server = "",
                port = "",
                secretKey = "",
                publicKey = "",
                preSharedKey = "",
                reserved = "",
                address = "",
                mtu = "",
                finalMask = "",
                jc = "4",
                jmin = "40",
                jmax = "70",
                s1 = "15",
                s2 = "30",
                s3 = "",
                s4 = "",
                h1 = "",
                h2 = "",
                h3 = "",
                h4 = "",
                i1 = "",
                i2 = "",
                i3 = "",
                i4 = "",
                i5 = "",
            ),
            ProxyHomeServerKind.OlcRtc to OlcRtc(
                remarks = "",
                provider = "jitsi",
                transport = "datachannel",
                roomUrl = "",
                encryptionKey = "",
                payload = "",
                localSocksPort = "10808",
            ),
            ProxyHomeServerKind.StrategyGroup to StrategyGroup(
                remarks = "",
                strategy = StrategyGroupConstants.TYPE_SELECT,
                subscriptionGroupId = null,
                filter = "",
                proxyServerIds = emptyList(),
                selectedMemberId = null,
                displayMode = StrategyGroupDisplayMode.ALWAYS,
                showInAutoBalancerList = true,
                sourceTrafficConfigId = null,
                sourcePolicyGroupName = "",
                probeInterval = "1m",
                probeUrl = "",
                enableBurstProbe = false,
                tolerance = "50ms",
                probeTimeout = "5s",
            ),
            ProxyHomeServerKind.ChainProxy to ChainProxy(
                remarks = "",
                proxyServerIds = emptyList(),
            ),
            ProxyHomeServerKind.Custom to Custom(
                remarks = "",
                overrideInboundAndDns = true,
                configJson = "",
            ),
        )

        cases.forEach { (kind, expected) ->
            val actual: ProxyServer<*> = createProxyServerDraft(kind)
            assertEquals(
                expected = expected,
                actual = actual,
                message = "Unexpected draft for $kind",
            )
        }
    }

    @Test
    fun eachCallReturnsAnIndependentMutableDraft() {
        val firstStrategy = assertIs<StrategyGroup>(createProxyServerDraft(ProxyHomeServerKind.StrategyGroup))
        val secondStrategy = assertIs<StrategyGroup>(createProxyServerDraft(ProxyHomeServerKind.StrategyGroup))

        assertNotSame(firstStrategy, secondStrategy)
        firstStrategy.proxyServerIds = listOf(42)
        assertEquals(emptyList(), secondStrategy.proxyServerIds)

        val firstVmess = assertIs<VMess>(createProxyServerDraft(ProxyHomeServerKind.Vmess))
        val secondVmess = assertIs<VMess>(createProxyServerDraft(ProxyHomeServerKind.Vmess))

        assertNotSame(firstVmess.parms, secondVmess.parms)
        firstVmess.parms.type = "ws"
        assertEquals("raw", secondVmess.parms.type)
    }

    private fun defaultV2RayParameters() = V2RayParameters(
        type = "raw",
        security = "none",
        path = null,
        host = null,
        headers = null,
        mtu = null,
        tti = null,
        seed = null,
        serviceName = null,
        mode = null,
        authority = null,
        extra = null,
        fm = null,
        fp = null,
        sni = null,
        alpn = null,
        ech = null,
        pcs = null,
        vcn = null,
        pbk = null,
        sid = null,
        pqv = null,
        spx = null,
        headerType = "none",
    )
}
