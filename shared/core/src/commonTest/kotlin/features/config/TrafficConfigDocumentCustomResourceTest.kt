// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrafficConfigDocumentCustomResourceTest {
    @Test
    fun roundTripsAnAsciiCustomResourceFileThroughTheProfileDocument() {
        val file = TrafficConfigCustomResourceFile(
            id = 6,
            name = "Extra",
            url = "https://geo.example/extra.dat",
        )
        val written = profileWith(file).withSkipiSettingsInRawConfig()

        assertTrue(written.rawConfig.contains("resource-custom-file = 6,Extra,https%3A%2F%2Fgeo.example%2Fextra.dat"))
        assertEquals(listOf(file), written.withSkipiSettingsReadFromRawConfig().resourceSettings.customFiles)
    }

    @Test
    fun decodesPercentEncodedUtf8AndLeavesLiteralPlusAsUriDecodeDoes() {
        val raw = """
            [SKIPI]
            resource-custom-file = 7,%D0%9F%D1%80%D0%B8%D0%BC%D0%B5%D1%80,https%3A%2F%2Fgeo.example%2F%D1%84%D0%B0%D0%B9%D0%BB+%2B.dat
        """.trimIndent()

        val restored = profileWithRaw(raw).withSkipiSettingsReadFromRawConfig()

        assertEquals(
            listOf(TrafficConfigCustomResourceFile(7, "Пример", "https://geo.example/файл++.dat")),
            restored.resourceSettings.customFiles,
        )
    }

    @Test
    fun malformedTrailingEscapeUsesAndroidUriDecodeReplacementCharacter() {
        val raw = """
            [SKIPI]
            resource-custom-file = 8,Name,https%
        """.trimIndent()

        val restored = profileWithRaw(raw).withSkipiSettingsReadFromRawConfig()

        assertEquals(
            listOf(TrafficConfigCustomResourceFile(8, "Name", "https\uFFFD")),
            restored.resourceSettings.customFiles,
        )
    }

    private fun profileWith(file: TrafficConfigCustomResourceFile): TrafficConfigState =
        TrafficConfigState(
            id = 1,
            name = "Profile",
            rawConfig = "[General]\nloglevel = warning\n",
            resourceSettings = TrafficConfigResourceSettings(customFiles = listOf(file)),
        )

    private fun profileWithRaw(raw: String): TrafficConfigState =
        TrafficConfigState(
            id = 1,
            name = "Profile",
            rawConfig = raw,
        )
}
