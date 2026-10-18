// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Classpath smoke test for the canonical resource assets shipped by Android. */
class DesktopBundledResourceAssetsTest {

    @Test
    fun packagesEveryCanonicalGeoAssetOnTheDesktopClasspath() {
        val paths = listOf(
            "geo/loyalsoldier/geoip.dat.xz",
            "geo/loyalsoldier/geosite.dat.xz",
            "geo/v2fly/geoip.dat.xz",
            "geo/v2fly/geosite.dat.xz",
            "geo/v2fly/geoip-only-cn-private.dat.xz",
            "geo/chocolate4u/geoip.dat.xz",
            "geo/chocolate4u/geosite.dat.xz",
            "geo/runetfreedom/geoip.dat.xz",
            "geo/runetfreedom/geosite.dat.xz",
            "geo/roscomvpn/geoip.dat.xz",
            "geo/roscomvpn/geosite.dat.xz",
        )

        paths.forEach(::assertPackagedXzAsset)
    }

    @Test
    fun packagesBothCanonicalDirectCidrAssetsOnTheDesktopClasspath() {
        listOf(
            "xray/direct-cidr-v4.txt.xz",
            "xray/direct-cidr-v6.txt.xz",
        ).forEach(::assertPackagedXzAsset)
    }

    private fun assertPackagedXzAsset(path: String) {
        val stream = assertNotNull(
            javaClass.classLoader.getResourceAsStream(path),
            "Expected canonical Android asset at Desktop classpath path $path",
        )

        stream.use { input ->
            val header = input.readNBytes(XzMagic.size)
            assertEquals(XzMagic.size, header.size, "$path should contain its complete XZ header")
            assertTrue(header.contentEquals(XzMagic), "$path should keep its XZ-compressed bytes")
        }
    }

    private companion object {
        val XzMagic = byteArrayOf(
            0xFD.toByte(),
            0x37.toByte(),
            0x7A.toByte(),
            0x58.toByte(),
            0x5A.toByte(),
            0x00.toByte(),
        )
    }
}
