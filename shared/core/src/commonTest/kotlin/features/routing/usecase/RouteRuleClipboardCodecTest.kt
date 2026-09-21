// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import features.clipboard.ClipboardImportException
import features.clipboard.ClipboardImportFailure
import features.clipboard.ClipboardImportMode
import features.routing.model.RouteRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RouteRuleClipboardCodecTest {
    @Test
    fun encodesAndDecodesNormalizedPortableRouteRules() {
        val encoded = encodeRouteRulesForClipboard(
            listOf(
                RouteRule(
                    id = 42,
                    remarks = "  Work  ",
                    outboundTag = " ",
                    domain = listOf(" domain:example.com ", "", "domain:example.com"),
                    ip = listOf(" 1.1.1.1 "),
                    process = listOf(" firefox ", "firefox"),
                    port = " 443 ",
                    protocol = " tls ",
                    network = " tcp ",
                ),
            ),
        )

        assertEquals(
            listOf(
                RouteRuleClipboardItem(
                    remarks = "Work",
                    domain = listOf("domain:example.com"),
                    ip = listOf("1.1.1.1"),
                    process = listOf("firefox"),
                    port = "443",
                    protocol = "tls",
                    network = "tcp",
                ),
            ),
            decodeRouteRulesFromClipboard(encoded),
        )
    }

    @Test
    fun acceptsLegacyV2RayRouteRuleClipboardFormat() {
        val rules = decodeRouteRulesFromClipboard(
            """
                [{
                  "remarks":"Legacy",
                  "outboundTag":"direct",
                  "domain":["domain:example.com"],
                  "protocol":["http","tls"]
                }]
            """.trimIndent(),
        )

        assertEquals(
            RouteRuleClipboardItem(
                remarks = "Legacy",
                outboundTag = "direct",
                domain = listOf("domain:example.com"),
                protocol = "http,tls",
            ),
            rules.single(),
        )
    }

    @Test
    fun mergesOnlyNewEffectiveRulesAndAssignsStableIds() {
        val existing = RouteRule(id = 7, outboundTag = "direct", domain = listOf("domain:example.com"))
        val result = applyRouteRuleClipboardImport(
            existingRules = listOf(existing),
            importedRules = listOf(
                RouteRuleClipboardItem(outboundTag = "direct", domain = listOf("domain:example.com")),
                RouteRuleClipboardItem(remarks = "New", outboundTag = "block", domain = listOf("domain:ads.example")),
            ),
            nextRuleId = 10,
            mode = ClipboardImportMode.Merge,
        )

        assertEquals(listOf(existing, RouteRule(id = 10, remarks = "New", outboundTag = "block", domain = listOf("domain:ads.example"))), result.rules)
        assertEquals(11, result.nextRuleId)
    }

    @Test
    fun reportsEmptyClipboardWithPortableFailure() {
        val error = assertFailsWith<ClipboardImportException> {
            decodeRouteRulesFromClipboard("\uFEFF  ")
        }

        assertEquals(ClipboardImportFailure.EmptyClipboard, error.failure)
    }
}
