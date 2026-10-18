// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.routing.RouteOutboundItem
import features.proxy.server.model.V2RayParameters
import features.proxy.server.model.V2RayTransportWebSocket
import features.proxy.server.model.VLESS
import features.routing.model.DefaultRouteOutboundTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteOutboundOptionPresentationTest {

    @Test
    fun staticTargetsKeepAndroidFlagsAndOptionalSummary() {
        val items = listOf(
            RouteOutboundItem.Static(tag = "PROXY", title = "Proxy"),
            RouteOutboundItem.Static(tag = DefaultRouteOutboundTag, title = "Proxy"),
            RouteOutboundItem.Static(tag = "DIRECT", title = "Direct"),
            RouteOutboundItem.Static(tag = "direct", title = "Direct"),
            RouteOutboundItem.Static(tag = "REJECT", title = "Block"),
            RouteOutboundItem.Static(tag = "block", title = "Block"),
            RouteOutboundItem.Static(tag = "CONFIG:42", title = "Other profile", summary = "CONFIG:42"),
            RouteOutboundItem.Static(tag = "custom-target", title = "Legacy target"),
            RouteOutboundItem.Static(tag = "Proxy", title = "Mixed case proxy"),
            RouteOutboundItem.Static(tag = "Direct", title = "Mixed case direct"),
            RouteOutboundItem.Static(tag = "Reject", title = "Mixed case reject"),
            RouteOutboundItem.Static(tag = "config:42", title = "Lowercase config target"),
        )

        val presented = items.map { item ->
            presentRouteOutboundOption(item, defaultProxyServerTemplate = "Proxy server {id}")
        }

        assertEquals(
            listOf("⚡", "⚡", "🔄", "🔄", "🚫", "🚫", "⚙️", "🌐", "🌐", "🌐", "🌐", "🌐"),
            presented.map { it.flag },
        )
        assertEquals("CONFIG:42", presented[6].subtitle)
        assertNull(presented[0].subtitle)
        assertEquals(items.map { it.tag }, presented.map { it.tag })
    }

    @Test
    fun serverPresentationUsesCountryFlagAndProtocolTransportText() {
        val record = ProxyServerRecord(
            id = 12,
            groupId = 1,
            server = VLESS(
                remarks = "  🇳🇱 Amsterdam",
                server = "example.com",
                port = "443",
                parms = V2RayParameters(type = V2RayTransportWebSocket, security = "reality"),
            ),
        )

        val presented = presentRouteOutboundOption(
            RouteOutboundItem.Server(server = record, tag = "Amsterdam"),
            defaultProxyServerTemplate = "Proxy server {id}",
        )

        assertEquals("server-12", presented.key)
        assertEquals("Amsterdam", presented.tag)
        assertEquals("🇳🇱", presented.flag)
        assertEquals("Amsterdam", presented.title)
        assertEquals("VLESS • WS • Reality", presented.subtitle)
    }

    @Test
    fun flagOnlyAndUnnamedServersRetainAndroidTitleFallbacks() {
        val flagOnly = presentRouteOutboundOption(
            RouteOutboundItem.Server(
                server = ProxyServerRecord(
                    id = 7,
                    groupId = 1,
                    server = VLESS(remarks = "🇫🇮", server = "flag.example", port = "443"),
                ),
                tag = "🇫🇮",
            ),
            defaultProxyServerTemplate = "Proxy server {id}",
        )
        val unnamed = presentRouteOutboundOption(
            RouteOutboundItem.Server(
                server = ProxyServerRecord(
                    id = 93,
                    groupId = 1,
                    server = VLESS(server = "unnamed.example", port = "443"),
                ),
                tag = "93",
            ),
            defaultProxyServerTemplate = "Proxy server {id}",
        )

        assertEquals("🇫🇮", flagOnly.flag)
        assertEquals("🇫🇮", flagOnly.title)
        assertEquals("VLESS • TCP", flagOnly.subtitle)
        assertNull(unnamed.flag)
        assertEquals("Proxy server 93", unnamed.title)
        assertEquals("VLESS • TCP", unnamed.subtitle)
    }

    @Test
    fun nonCountryEmojiAndPaddedFlagOnlyRemarksPreserveAndroidRawTitles() {
        val rawNonCountryEmojiRemarks = "  🚀 Node  "
        val rawPaddedFlagOnlyRemarks = "  🇫🇮  "
        val nonCountryEmoji = presentRouteOutboundOption(
            RouteOutboundItem.Server(
                server = ProxyServerRecord(
                    id = 81,
                    groupId = 1,
                    server = VLESS(
                        remarks = rawNonCountryEmojiRemarks,
                        server = "emoji.example",
                        port = "443",
                    ),
                ),
                tag = rawNonCountryEmojiRemarks,
            ),
            defaultProxyServerTemplate = "Proxy server {id}",
        )
        val paddedFlagOnly = presentRouteOutboundOption(
            RouteOutboundItem.Server(
                server = ProxyServerRecord(
                    id = 82,
                    groupId = 1,
                    server = VLESS(
                        remarks = rawPaddedFlagOnlyRemarks,
                        server = "flag-only.example",
                        port = "443",
                    ),
                ),
                tag = rawPaddedFlagOnlyRemarks,
            ),
            defaultProxyServerTemplate = "Proxy server {id}",
        )

        assertNull(nonCountryEmoji.flag)
        assertEquals(rawNonCountryEmojiRemarks, nonCountryEmoji.title)
        assertEquals("🇫🇮", paddedFlagOnly.flag)
        assertEquals(rawPaddedFlagOnlyRemarks, paddedFlagOnly.title)
    }
}
