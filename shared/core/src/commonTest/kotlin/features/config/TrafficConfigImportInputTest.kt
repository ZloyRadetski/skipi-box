// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrafficConfigImportInputTest {
    private val document = "[Rule]\nFINAL,PROXY"
    private val encodedDocument = "W1J1bGVdCkZJTkFMLFBST1hZ"
    private val url = "https://example.test/rules.conf"
    private val encodedUrl = "aHR0cHM6Ly9leGFtcGxlLnRlc3QvcnVsZXMuY29uZg=="
    private val percentUrl = "https%3A%2F%2Fexample.test%2Frules.conf"

    @Test
    fun pasted_config_links_are_decoded_and_keep_their_activation() {
        assertEquals(
            TrafficConfigImportInput.Document(document, activate = false),
            parseTrafficConfigImportInput("  skipi://conf/add/$encodedDocument\n"),
        )
        assertEquals(
            TrafficConfigImportInput.Url(url, activate = true),
            parseTrafficConfigImportInput("skipi://conf/onadd/$encodedUrl"),
        )
        assertEquals(
            TrafficConfigImportInput.Url(url, activate = false),
            parseTrafficConfigImportInput("SKIPI://ROUTING/ADD/$percentUrl"),
        )
    }

    @Test
    fun pasted_urls_and_documents_are_taken_as_they_are() {
        assertEquals(
            TrafficConfigImportInput.Url(url, activate = false),
            parseTrafficConfigImportInput(" $url "),
        )
        assertEquals(
            TrafficConfigImportInput.Document(document, activate = false),
            parseTrafficConfigImportInput("$document\n"),
        )
    }

    @Test
    fun a_skipi_link_that_carries_no_config_is_refused() {
        listOf(
            "skipi://add/$percentUrl",
            "skipi://toggle",
            "skipi://conf/add/",
            "skipi://nonsense",
        ).forEach { link ->
            assertFailsWith<IllegalArgumentException>(link) { parseTrafficConfigImportInput(link) }
        }
    }

    @Test
    fun provider_payloads_decode_from_base64_and_percent_encoding() {
        assertEquals(url, url.resolveSkipiConfigPayload())
        assertEquals(url, percentUrl.resolveSkipiConfigPayload())
        assertEquals(url, encodedUrl.resolveSkipiConfigPayload())
        assertEquals(document, encodedDocument.resolveSkipiConfigPayload())
        assertEquals(document, " $document ".resolveSkipiConfigPayload())
    }

    @Test
    fun only_a_text_with_a_section_and_no_errors_is_a_config_document() {
        assertNull("# name: A\n[General]\nloglevel = info\n".trafficConfigDocumentErrorOrNull())
        assertNotNull("skipi://conf/add/$encodedDocument\n".trafficConfigDocumentErrorOrNull())
        assertNotNull(url.trafficConfigDocumentErrorOrNull())
        assertNotNull("<html><body>404</body></html>".trafficConfigDocumentErrorOrNull())
        assertEquals(
            "Only one FINAL rule is allowed",
            "[Rule]\nFINAL,PROXY\nFINAL,DIRECT".trafficConfigDocumentErrorOrNull(),
        )
    }
}
