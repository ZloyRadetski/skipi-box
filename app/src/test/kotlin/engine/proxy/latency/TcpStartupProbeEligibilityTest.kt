// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy.latency

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.Hysteria2
import features.proxy.server.model.OlcRtc
import features.proxy.server.model.V2RayParameters
import features.proxy.server.model.VLESS
import features.proxy.server.model.Wireguard
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TcpStartupProbeEligibilityTest {
    @Test
    fun tcp_transports_can_use_the_short_startup_socket_check() {
        assertTrue(
            VLESS(
                server = "reality.example",
                port = "443",
                parms = V2RayParameters(type = "raw"),
            ).supportsTcpStartupProbe(),
        )
        assertTrue(
            VLESS(
                server = "xhttp.example",
                port = "443",
                parms = V2RayParameters(type = "xhttp"),
            ).supportsTcpStartupProbe(),
        )
    }

    @Test
    fun udp_quic_and_webrtc_transports_are_never_validated_by_tcp() {
        assertFalse(Hysteria2(server = "hy2.example", port = "443").supportsTcpStartupProbe())
        assertFalse(Wireguard(server = "wg.example", port = "51820").supportsTcpStartupProbe())
        assertFalse(AmneziaWg(server = "awg.example", port = "51820").supportsTcpStartupProbe())
        assertFalse(OlcRtc(roomUrl = "https://rtc.example/room").supportsTcpStartupProbe())
        assertFalse(
            VLESS(
                server = "kcp.example",
                port = "443",
                parms = V2RayParameters(type = "mkcp"),
            ).supportsTcpStartupProbe(),
        )
    }
}
