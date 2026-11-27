// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultilinkSettingsTest {
    private fun core(settings: MultilinkSettings, running: Boolean = true): JsonObject =
        Json.parseToJsonElement(settings.toCoreJson(tunnelRunning = running)).jsonObject

    @Test
    fun offByDefault() {
        val json = core(MultilinkSettings())
        assertFalse(json.getValue("enabled").jsonPrimitive.boolean)
    }

    /** Core merges only while a tunnel runs: an idle SKIPI sends no probes over the user's links. */
    @Test
    fun onlyEnabledWhileTheTunnelRuns() {
        val on = MultilinkSettings(enabled = true)
        assertTrue(core(on, running = true).getValue("enabled").jsonPrimitive.boolean)
        assertFalse(core(on, running = false).getValue("enabled").jsonPrimitive.boolean)
    }

    @Test
    fun coreGetsEverySettingByAdapterIdButNotTheDisplayNames() {
        val settings = MultilinkSettings(
            enabled = true,
            excluded = listOf("{wifi}"),
            weights = mapOf("{eth}" to 3.0),
            limits = mapOf("{eth}" to 25.0),
            phones = listOf("{usb}"),
            phoneCooling = true,
            coolMbps = 10.0,
            probeTargets = listOf("77.88.8.8:443"),
            names = mapOf("{eth}" to "Home line"),
        )
        val json = core(settings)
        assertEquals("{wifi}", json.getValue("excluded").jsonArray.single().jsonPrimitive.content)
        assertEquals(3.0, json.getValue("weights").jsonObject.getValue("{eth}").jsonPrimitive.double)
        assertEquals(25.0, json.getValue("limits").jsonObject.getValue("{eth}").jsonPrimitive.double)
        assertEquals("{usb}", json.getValue("phones").jsonArray.single().jsonPrimitive.content)
        assertTrue(json.getValue("phoneCooling").jsonPrimitive.boolean)
        assertEquals(10.0, json.getValue("coolMbps").jsonPrimitive.double)
        assertEquals("77.88.8.8:443", json.getValue("probeTargets").jsonArray.single().jsonPrimitive.content)
        assertNull(json["names"], "display names stay in the app")
    }

    @Test
    fun excludingAndIncludingALink() {
        val excluded = MultilinkSettings().withLinkUsed("{wifi}", used = false)
        assertEquals(listOf("{wifi}"), excluded.excluded)
        assertEquals(excluded, excluded.withLinkUsed("{wifi}", used = false), "excluding twice changes nothing")
        assertEquals(emptyList(), excluded.withLinkUsed("{wifi}", used = true).excluded)
    }

    @Test
    fun aLimitOfZeroRemovesTheCap() {
        val capped = MultilinkSettings().withLimit("{eth}", 10.0)
        assertEquals(mapOf("{eth}" to 10.0), capped.limits)
        assertEquals(emptyMap(), capped.withLimit("{eth}", 0.0).limits)
    }

    @Test
    fun aBlankNameFallsBackToTheAdapterName() {
        val named = MultilinkSettings().withName("{eth}", "  Home line ")
        assertEquals("Home line", named.displayName("{eth}", "Ethernet 2"))
        assertEquals("Ethernet 2", named.withName("{eth}", " ").displayName("{eth}", "Ethernet 2"))
        assertEquals(emptyMap(), named.withName("{eth}", "").names)
    }

    /** The fields an older settings file lacks take their defaults, so SKIPI starts as it did. */
    @Test
    fun missingFieldsTakeTheirDefaults() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString(MultilinkSettings.serializer(), """{"enabled":true}""")
        assertEquals(MultilinkSettings(enabled = true), decoded)
    }

    @Test
    fun parsesCoreStatus() {
        val status = parseMultilinkStatus(
            """{"enabled":true,"merging":true,"links":[{"id":"{eth}","name":"Ethernet 2","kind":"ethernet",
            |"ipv4":"192.168.1.200","state":"up","state6":"-","excluded":false,"phone":false,"weight":1,"flows":3,
            |"rateIn":125000,"rateOut":12500,"linkIn":130000,"linkOut":13000,"limitMbps":0,"coolMbps":0,"future":1}],
            |"skipped":[{"name":"Radmin VPN","reason":"virtual/VPN adapter"}]}""".trimMargin(),
        )
        assertTrue(status.merging)
        val link = status.links.single()
        assertEquals("{eth}", link.id)
        assertEquals(MultilinkLinkKind.Ethernet, link.kind)
        assertEquals(1.04, link.mbpsDown(), 0.001, "the link as a whole, every app included")
        assertEquals(3, link.flows)
        assertEquals("Radmin VPN", status.skipped.single().name)
    }

    @Test
    fun unreadableStatusIsOff() {
        assertEquals(MultilinkStatus(), parseMultilinkStatus("not json"))
    }

    @Test
    fun storedSettingsReadBackAndAnythingUnreadableIsOff() {
        val settings = MultilinkSettings(enabled = true).withLimit("mobile", 5.0).withLinkUsed("wifi", used = false)
        assertEquals(settings, decodeMultilinkSettings(encodeMultilinkSettings(settings)))
        assertEquals(MultilinkSettings(), decodeMultilinkSettings(null))
        assertEquals(MultilinkSettings(), decodeMultilinkSettings("not json"))
    }

    @Test
    fun linksReportedByAndroidCarryTheirNetworkHandle() {
        val json = listOf(
            MultilinkHostLink(id = "wifi", name = "Wi-Fi", kind = MultilinkLinkKind.WiFi, ipv4 = "192.0.2.10", handle = 11),
            MultilinkHostLink(id = "mobile", name = "Mobile data", kind = MultilinkLinkKind.Mobile, ipv6 = "2001:db8::7", handle = 12, phone = true),
        ).toCoreLinksJson()
        val parsed = Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonArray
        val mobile = parsed[1] as JsonObject
        assertEquals("mobile", (mobile["kind"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("12", (mobile["handle"] as kotlinx.serialization.json.JsonPrimitive).content)
    }
}
