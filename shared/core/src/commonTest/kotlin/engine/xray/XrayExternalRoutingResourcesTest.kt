// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.routing.model.RouteRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Install only after the Android baseline oracle passes and before shared API RED. */
class XrayExternalRoutingResourcesTest {
    @Test
    fun validatesOnlyEnabledDomainCandidatesAndChecksDistinctFilesInEncounterOrder() {
        val checkedFiles = mutableListOf<String>()
        validateXrayExternalDomainResources(
            routeRules = listOf(
                rule(1, listOf(" ext:geosite.dat:cn ", "ext:geosite.dat:cn", "ext:custom.dat:tag"), ip = listOf("ext:ip-only.dat:tag")),
                rule(2, listOf("ext:disabled.dat:tag", "ext:malformed"), enabled = false),
                rule(3, listOf("plain.example")),
            ),
            hasNonEmptyFile = { fileName -> checkedFiles += fileName; true },
        )

        assertEquals(listOf("geosite.dat", "custom.dat"), checkedFiles)
    }

    @Test
    fun rejectsMalformedCandidatesBeforeConsultingFiles() {
        val checkedFiles = mutableListOf<String>()
        val error = runCatching {
            validateXrayExternalDomainResources(
                routeRules = listOf(rule(4, listOf("ext:broken", "ext:missing.dat:tag"))),
                hasNonEmptyFile = { fileName -> checkedFiles += fileName; false },
            )
        }.exceptionOrNull()

        val failure = assertIs<IllegalStateException>(error)
        assertEquals("Invalid external routing domain rule: ext:broken", failure.message)
        assertTrue(checkedFiles.isEmpty())
    }

    @Test
    fun reportsDistinctMissingFilesInEncounterOrder() {
        val checkedFiles = mutableListOf<String>()
        val error = runCatching {
            validateXrayExternalDomainResources(
                routeRules = listOf(
                    rule(5, listOf("ext:first.dat:a", "ext:first.dat:b", "ext:second.dat:c")),
                ),
                hasNonEmptyFile = { fileName -> checkedFiles += fileName; false },
            )
        }.exceptionOrNull()

        val failure = assertIs<IllegalStateException>(error)
        assertEquals(listOf("first.dat", "second.dat"), checkedFiles)
        assertEquals("Missing external routing resource file: first.dat, second.dat", failure.message)
    }

    @Test
    fun preflightKeepsTheParsedFilenameLiteralWithoutAddingDatSuffix() {
        val checkedFiles = mutableListOf<String>()

        validateXrayExternalDomainResources(
            routeRules = listOf(rule(6, listOf("ext:custom:tag"))),
            hasNonEmptyFile = { fileName -> checkedFiles += fileName; true },
        )

        assertEquals(listOf("custom"), checkedFiles)
    }

    private fun rule(
        id: Int,
        domain: List<String>,
        ip: List<String> = emptyList(),
        enabled: Boolean = true,
    ) = RouteRule(id = id, outboundTag = "direct", domain = domain, ip = ip, enabled = enabled)
}
