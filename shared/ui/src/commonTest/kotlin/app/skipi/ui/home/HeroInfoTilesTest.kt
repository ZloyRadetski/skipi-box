// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeroInfoTilesTest {
    private val ping = SkipiProxyHeroLatency(text = "120 ms", color = Color.Green)

    @Test
    fun tilesAreOnlyShownWhileConnected() {
        for (phase in listOf(SkipiConnectionHeroPhase.Disconnected, SkipiConnectionHeroPhase.Connecting)) {
            val tiles = skipiHeroInfoTiles(
                phase = phase,
                sessionDurationText = "00:12",
                latency = ping,
                memoryText = "40 MB",
                ip = SkipiHeroIp(value = "203.0.113.7", flag = null),
                showIp = true,
            )
            assertTrue(tiles.isEmpty(), "no tiles in $phase")
        }
    }

    @Test
    fun connectedShowsTimePingMemoryAndIpInThatOrder() {
        val tiles = skipiHeroInfoTiles(
            phase = SkipiConnectionHeroPhase.Connected,
            sessionDurationText = "00:12",
            latency = ping,
            memoryText = "40 MB",
            ip = SkipiHeroIp(value = "203.0.113.7", flag = "🇳🇱"),
            showIp = true,
        )
        assertEquals(
            listOf(SkipiHeroTileKind.Time, SkipiHeroTileKind.Ping, SkipiHeroTileKind.Memory, SkipiHeroTileKind.Ip),
            tiles.map { it.kind },
        )
        assertEquals(listOf("00:12", "120 ms", "40 MB", "203.0.113.7"), tiles.map { it.value })
        assertEquals(Color.Green, tiles[1].color)
        assertEquals("🇳🇱", tiles[3].flag)
    }

    @Test
    fun missingValuesDropTheirTilesButAnUnknownIpShowsAPlaceholder() {
        val tiles = skipiHeroInfoTiles(
            phase = SkipiConnectionHeroPhase.Connected,
            sessionDurationText = "00:12",
            latency = null,
            memoryText = null,
            ip = null,
            showIp = true,
        )
        assertEquals(listOf(SkipiHeroTileKind.Time, SkipiHeroTileKind.Ip), tiles.map { it.kind })
        assertEquals("—", tiles.last().value)
        assertNull(tiles.last().flag)
    }

    @Test
    fun maskingHidesTheTrailingPartOfTheAddress() {
        assertEquals("203.0.•••.•••", maskIpAddress("203.0.113.7"))
        assertEquals("2001:db8:•••", maskIpAddress("2001:db8:85a3::8a2e:370:7334"))
        assertEquals("—", maskIpAddress("—"))
        assertEquals("•••", maskIpAddress("localhost"))
    }

    @Test
    fun maskedTileShowsTheMaskedAddressButKeepsTheFlag() {
        val tiles = skipiHeroInfoTiles(
            phase = SkipiConnectionHeroPhase.Connected,
            sessionDurationText = null,
            latency = null,
            memoryText = null,
            ip = SkipiHeroIp(value = "203.0.113.7", flag = "F"),
            showIp = true,
            maskIp = true,
        )
        assertEquals("203.0.•••.•••", tiles.single().value)
        assertEquals("F", tiles.single().flag)
    }

    @Test
    fun disabledIpSettingHidesTheIpTile() {
        val tiles = skipiHeroInfoTiles(
            phase = SkipiConnectionHeroPhase.Connected,
            sessionDurationText = "00:12",
            latency = ping,
            memoryText = null,
            ip = SkipiHeroIp(value = "203.0.113.7", flag = null),
            showIp = false,
        )
        assertEquals(listOf(SkipiHeroTileKind.Time, SkipiHeroTileKind.Ping), tiles.map { it.kind })
    }
}
