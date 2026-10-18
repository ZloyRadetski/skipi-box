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

    @Test
    fun decodesMalformedCustomResourceComponentsLikeAndroidUri() {
        val cases = listOf(
            "%A" to "\uFFFD",
            "%GG" to "\uFFFD\u0000G",
            "%C3%28" to "\uFFFD(",
        )

        cases.forEachIndexed { index, (encoded, expectedDecoded) ->
            val id = 20 + index
            val restored = profileWithRaw(
                """
                    [SKIPI]
                    resource-custom-file = $id,Name,https$encoded
                """.trimIndent(),
            ).withSkipiSettingsReadFromRawConfig()

            assertEquals(
                listOf(TrafficConfigCustomResourceFile(id, "Name", "https$expectedDecoded")),
                restored.resourceSettings.customFiles,
                "Android Uri.decode compatibility for $encoded",
            )
        }
    }

    @Test
    fun filtersInvalidCustomResourceRowsAndKeepsTheFirstPositiveId() {
        val raw = """
            [SKIPI]
            resource-custom-file = 0,Zero,https%3A%2F%2Fgeo.example%2Fzero.dat
            resource-custom-file = -1,Negative,https%3A%2F%2Fgeo.example%2Fnegative.dat
            resource-custom-file = 2,,https%3A%2F%2Fgeo.example%2Fblank-name.dat
            resource-custom-file = 3,Blank URL,
            resource-custom-file = 6,%20%20First%20name%20%20,%20%20https%3A%2F%2Fgeo.example%2Ffirst.dat%20%20
            resource-custom-file = 6,Second%20name,https%3A%2F%2Fgeo.example%2Fsecond.dat
        """.trimIndent()

        val restored = profileWithRaw(raw).withSkipiSettingsReadFromRawConfig()

        assertEquals(
            listOf(TrafficConfigCustomResourceFile(6, "First name", "https://geo.example/first.dat")),
            restored.resourceSettings.customFiles,
        )
        assertEquals(7, restored.resourceSettings.nextCustomFileId)
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
