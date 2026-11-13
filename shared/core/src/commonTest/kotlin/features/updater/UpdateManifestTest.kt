// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateManifestTest {
    private val current = CurrentAppVersion(name = "0.5.0-rc.9", code = 900)

    private fun manifest(
        code: Int = 923,
        name: String = "0.5.0-rc.10",
        extra: String = "",
        assets: String = """
            {"name":"SKIPI-universal.apk","url":"https://example.invalid/u.apk","size":10,"sha256":"aa"},
            {"name":"SKIPI-arm64-v8a.apk","url":"https://example.invalid/a64.apk","size":9,"sha256":"bb"},
            {"name":"SKIPI-windows-x64.msi","url":"https://example.invalid/w.msi","size":99,"sha256":"cc"}
        """,
    ) = """{"schema":1,"versionName":"$name","versionCode":$code,"tag":"v$name","title":"SKIPI v$name",
        "releaseUrl":"https://example.invalid/r","publishedAt":"2026-11-13T10:00:00Z","changelog":"- A\n- B",
        "assets":[$assets]$extra}"""

    @Test
    fun aValidManifestIsReadAndUnknownFieldsAreIgnored() {
        val parsed = UpdateManifestParser.parse(manifest(extra = ""","future":{"x":1}"""))

        assertNotNull(parsed)
        assertEquals(923, parsed.versionCode)
        assertEquals("0.5.0-rc.10", parsed.versionName)
        assertEquals(3, parsed.assets.size)
    }

    @Test
    fun aBodyThatIsNotAManifestIsRejected() {
        assertNull(UpdateManifestParser.parse("<html>"))
        assertNull(UpdateManifestParser.parse("[]"))
        assertNull(UpdateManifestParser.parse("""{"tag_name":"v1","assets":[]}"""))
        assertNull(UpdateManifestParser.parse(manifest().replace("\"schema\":1", "\"schema\":0")))
        assertNull(UpdateManifestParser.parse(manifest(code = 0)))
        assertNull(UpdateManifestParser.parse(manifest(name = " ")))
        assertFalse(UpdateManifestParser.isManifest("not json"))
        assertTrue(UpdateManifestParser.isManifest(manifest()))
    }

    @Test
    fun theBuildNumberDecidesWhetherAManifestIsNewerNotTheName() {
        val newer = UpdateManifestParser.parse(manifest(code = 901))!!
        val same = UpdateManifestParser.parse(manifest(code = 900, name = "0.6.0"))!!
        val older = UpdateManifestParser.parse(manifest(code = 899, name = "9.9.9"))!!

        assertNotNull(UpdateManifestParser.androidOffer(newer, current, emptyList()))
        assertNull(UpdateManifestParser.androidOffer(same, current.copy(name = "0.6.0"), emptyList()))
        assertNull(UpdateManifestParser.androidOffer(older, current, emptyList()))
    }

    @Test
    fun androidTakesTheApkOfItsAbiThenUniversalAndCarriesTheDigest() {
        val parsed = UpdateManifestParser.parse(manifest())!!

        val arm = UpdateManifestParser.androidOffer(parsed, current, listOf("arm64-v8a", "armeabi-v7a"))!!
        assertEquals("SKIPI-arm64-v8a.apk", arm.assetName)
        assertEquals("https://example.invalid/a64.apk", arm.downloadUrl)
        assertEquals("sha256:bb", arm.assetSha256)
        assertEquals(923, arm.versionCode)
        assertEquals("- A\n- B", arm.changelog)

        val other = UpdateManifestParser.androidOffer(parsed, current, listOf("x86"))!!
        assertEquals("SKIPI-universal.apk", other.assetName)
    }

    @Test
    fun androidWithoutAnyApkGetsNoOffer() {
        val parsed = UpdateManifestParser.parse(manifest(assets = """{"name":"SKIPI-windows-x64.msi","url":"u","size":1,"sha256":"c"}"""))!!

        assertNull(UpdateManifestParser.androidOffer(parsed, current, emptyList()))
    }

    @Test
    fun desktopTakesTheAssetOfItsPackageOrFallsBackToTheReleasePage() {
        val parsed = UpdateManifestParser.parse(manifest())!!
        val msi = DesktopUpdatePackage.entries.first { it.assetName == "SKIPI-windows-x64.msi" }
        val deb = DesktopUpdatePackage.entries.first { it.assetName.endsWith(".deb") }

        val withAsset = UpdateManifestParser.desktopOffer(parsed, current, msi)!!
        assertEquals("https://example.invalid/w.msi", withAsset.downloadUrl)
        assertEquals("SKIPI-windows-x64.msi", withAsset.assetName)
        assertEquals("sha256:cc", withAsset.assetSha256)
        assertTrue(withAsset.canDownload)

        val withoutAsset = UpdateManifestParser.desktopOffer(parsed, current, deb)!!
        assertEquals("https://example.invalid/r", withoutAsset.downloadUrl)
        assertFalse(withoutAsset.canDownload)
    }

    @Test
    fun eachChannelHasItsOwnFixedManifestAddress() {
        assertEquals(
            "https://github.com/ZloyRadetski/skipi-box/releases/download/update-manifests/stable.json",
            AppUpdateChannel.Stable.manifestUrl,
        )
        assertEquals(
            "https://github.com/ZloyRadetski/skipi-box/releases/download/update-manifests/nightly.json",
            AppUpdateChannel.Nightly.manifestUrl,
        )
    }
}
