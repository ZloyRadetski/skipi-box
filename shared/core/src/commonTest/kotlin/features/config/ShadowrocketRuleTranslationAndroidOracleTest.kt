// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import engine.xray.TrafficProfileServerRecord
import engine.xray.TrafficProfileXrayCapabilities
import engine.xray.TrafficProfileXrayPlanner
import features.proxy.server.model.ProxyServer
import features.routing.model.RouteRule
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Literal characterization of the Android runtime rule converter shared with the Desktop planner. */
class ShadowrocketRuleTranslationAndroidOracleTest {
    @Test
    fun android_runtime_planner_matches_frozen_rule_fixtures() {
        val actual = androidPlan().routeRules.map { route -> route.id to route.toPortableMatch() }
        val expected = fixtures.mapIndexedNotNull { index, fixture ->
            fixture.expected?.let { (index + 1) to it }
        }

        assertEquals(expected, actual)
    }

    @Test
    fun android_omitted_url_rule_does_not_resolve_its_unknown_policy() {
        val result = androidPlan("[Rule]\nRULE-SET,https://rules.example/list,Missing\nFINAL,PROXY\n")

        assertEquals(emptyList(), result.routeRules)
    }

    @Test
    fun desktop_profile_planner_matches_android_portable_rule_fixtures() {
        val actual = desktopPlan(fixtures)
            .routingRules
            .dropLast(1) // The profile planner appends its host-specific FINAL catch-all rule.
            .map { rule -> rule.toPortableMatch() }
        val expected = fixtures.mapNotNull(Fixture::expected)

        assertEquals(expected, actual)
    }

    private fun androidPlan(rawConfig: String = fixtureConfig()) = TrafficConfigRuntimePlanner.plan(
        TrafficConfigRuntimePlanInput(
            rawConfig = rawConfig,
            configs = emptyList(),
            servers = emptyList(),
            enableIpv6Fallback = false,
            enableIpv6PreferFallback = false,
        ),
    )

    private fun desktopPlan(rules: List<Fixture>) = TrafficProfileXrayPlanner.plan(
        profileContent = fixtureConfig(rules),
        servers = listOf(
            TrafficProfileServerRecord(
                id = 1,
                server = ProxyServer.parse(
                    "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@profile.example:443#Selected",
                ),
            ),
        ),
        selectedServerId = 1,
        capabilities = TrafficProfileXrayCapabilities(
            supportedExternalRuleSetFiles = setOf("geosite.dat", "geoip.dat"),
        ),
    )

    private fun fixtureConfig(rules: List<Fixture> = fixtures): String = buildString {
        appendLine("[Rule]")
        rules.forEach { fixture ->
            appendLine("${fixture.type},${fixture.value},PROXY")
        }
        appendLine("FINAL,PROXY")
    }

    private data class Fixture(
        val type: String,
        val value: String,
        val expected: PortableMatch?,
    )

    private data class PortableMatch(
        val domain: List<String> = emptyList(),
        val ip: List<String> = emptyList(),
        val process: List<String> = emptyList(),
        val port: String = "",
        val protocol: String = "",
        val network: String = "",
    )

    private fun RouteRule.toPortableMatch() = PortableMatch(
        domain = domain,
        ip = ip,
        process = process,
        port = port,
        protocol = protocol,
        network = network,
    )

    private fun JsonObject.toPortableMatch() = PortableMatch(
        domain = getValueList("domain"),
        ip = getValueList("ip"),
        process = getValueList("process"),
        port = this["port"]?.jsonPrimitive?.content.orEmpty(),
        protocol = getValueList("protocol").singleOrNull().orEmpty(),
        network = this["network"]?.jsonPrimitive?.content.orEmpty(),
    )

    private fun JsonObject.getValueList(key: String): List<String> =
        (this[key] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()

    private companion object {
        val fixtures = listOf(
            Fixture("DOMAIN", "example.test", PortableMatch(domain = listOf("full:example.test"))),
            Fixture("DOMAIN-SUFFIX", "example.test", PortableMatch(domain = listOf("domain:example.test"))),
            Fixture("DOMAIN-KEYWORD", "api", PortableMatch(domain = listOf("keyword:api"))),
            Fixture(
                "DOMAIN-WILDCARD",
                "api+*.example?.test",
                PortableMatch(domain = listOf("regexp:^api\\+.*\\.example.\\.test$")),
            ),
            // Android keeps a DOMAIN-SET value verbatim; it does not add geosite:.
            Fixture("DOMAIN-SET", "category-ads-all", PortableMatch(domain = listOf("category-ads-all"))),
            Fixture("GEOSITE", "CN", PortableMatch(domain = listOf("geosite:CN"))),
            Fixture("GEOSITE", "EXT:geosite.dat:private", PortableMatch(domain = listOf("EXT:geosite.dat:private"))),
            Fixture("IP-CIDR", "192.0.2.0/24", PortableMatch(ip = listOf("192.0.2.0/24"))),
            Fixture("IP-CIDR6", "2001:db8::/32", PortableMatch(ip = listOf("2001:db8::/32"))),
            Fixture("IP-CIDR", "not-an-ip", null),
            Fixture("IP-CIDR6", "also-not-an-ip", null),
            Fixture("IP-ASN", "AS13335", PortableMatch(ip = listOf("geoip:as13335"))),
            Fixture("IP-ASN", "geoip:cn", PortableMatch(ip = listOf("geoip:cn"))),
            Fixture("IP-ASN", "EXT:geoip.dat:private", PortableMatch(ip = listOf("EXT:geoip.dat:private"))),
            Fixture("GEOIP", "CN", PortableMatch(ip = listOf("geoip:CN"))),
            Fixture("GEOIP", "ext:geoip.dat:private", PortableMatch(ip = listOf("ext:geoip.dat:private"))),
            Fixture("RULE-SET", "Geosite:CN", PortableMatch(domain = listOf("Geosite:CN"))),
            Fixture("RULE-SET", "domain:example.test", PortableMatch(domain = listOf("domain:example.test"))),
            Fixture("RULE-SET", "Domain:example.test", PortableMatch(domain = listOf("geosite:Domain:example.test"))),
            Fixture("RULE-SET", "geoip:private", PortableMatch(ip = listOf("geoip:private"))),
            // Android's generic ext: handling intentionally contributes the raw value to both fields.
            Fixture(
                "RULE-SET",
                "ext:geosite.dat:private",
                PortableMatch(domain = listOf("ext:geosite.dat:private"), ip = listOf("ext:geosite.dat:private")),
            ),
            Fixture(
                "RULE-SET",
                "ext:geoip.dat:private",
                PortableMatch(domain = listOf("ext:geoip.dat:private"), ip = listOf("ext:geoip.dat:private")),
            ),
            Fixture("RULE-SET", "198.51.100.0/24", PortableMatch(ip = listOf("198.51.100.0/24"))),
            // Android's current runtime converter omits URL-backed rule sets and unknown rule types.
            Fixture("RULE-SET", "https://rules.example/list", null),
            Fixture("URL-REGEX", "example.test", null),
            Fixture("PROCESS-NAME", "firefox.exe", PortableMatch(process = listOf("firefox.exe"))),
            Fixture("DST-PORT", "443", PortableMatch(port = "443")),
            Fixture("PROTOCOL", "bittorrent", PortableMatch(protocol = "bittorrent")),
            Fixture("NETWORK", "tcp", PortableMatch(network = "tcp")),
        )

    }
}
