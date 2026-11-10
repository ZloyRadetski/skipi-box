// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubReleaseParserTest {
    private val current = CurrentAppVersion(name = "0.4.1", code = 180)

    private fun release(
        tag: String = "v0.5.0",
        body: String = "## SKIPI\n\n- Version code: 190",
        assets: String = """
            {"name": "SKIPI-universal.apk", "browser_download_url": "https://example.invalid/u.apk", "size": 100,
             "digest": "sha256:${"ab".repeat(32)}"},
            {"name": "SKIPI-arm64-v8a.apk", "browser_download_url": "https://example.invalid/a.apk", "size": 60},
            {"name": "notes.txt", "browser_download_url": "https://example.invalid/n.txt", "size": 1}
        """.trimIndent(),
    ) = """
        {"tag_name": "$tag", "name": "Release title", "body": "${body.replace("\n", "\\n")}",
         "published_at": "2026-10-01T00:00:00Z", "assets": [$assets]}
    """.trimIndent()

    @Test
    fun newerReleasePicksTheAssetMatchingTheFirstSupportedAbi() {
        val update = GitHubReleaseParser.parse(release(), current, supportedAbis = listOf("arm64-v8a", "armeabi-v7a"))

        assertEquals("0.5.0", update?.versionName)
        assertEquals(190, update?.versionCode)
        assertEquals("SKIPI-arm64-v8a.apk", update?.assetName)
        assertEquals("https://example.invalid/a.apk", update?.downloadUrl)
        assertEquals(60L, update?.apkSizeBytes)
        assertEquals("Release title", update?.releaseTitle)
        assertEquals("2026-10-01T00:00:00Z", update?.publishedAt)
        assertNull(update?.assetSha256)
    }

    @Test
    fun withoutAnAbiMatchTheUniversalApkIsUsedAndItsSha256IsKept() {
        val update = GitHubReleaseParser.parse(release(), current, supportedAbis = listOf("riscv64"))

        assertEquals("SKIPI-universal.apk", update?.assetName)
        assertEquals("sha256:${"ab".repeat(32)}", update?.assetSha256)
    }

    @Test
    fun withoutAnyHintTheFirstApkWins() {
        val assets = """
            {"name": "first.apk", "browser_download_url": "https://example.invalid/1.apk", "size": 1},
            {"name": "second.apk", "browser_download_url": "https://example.invalid/2.apk", "size": 2}
        """.trimIndent()

        val update = GitHubReleaseParser.parse(release(assets = assets), current, supportedAbis = emptyList())

        assertEquals("first.apk", update?.assetName)
    }

    @Test
    fun upToDateOrUnusableReleasesYieldNoUpdate() {
        assertNull(GitHubReleaseParser.parse(release(tag = "v0.4.1", body = "Version code: 180"), current, emptyList()))
        assertNull(GitHubReleaseParser.parse(release(tag = "v0.3.0", body = ""), current, emptyList()))
        assertNull(GitHubReleaseParser.parse(release(tag = ""), current, emptyList()))
        assertNull(GitHubReleaseParser.parse(release(assets = ""), current, emptyList()))
        assertNull(GitHubReleaseParser.parse("not json", current, emptyList()))
        assertNull(GitHubReleaseParser.parse("""{"tag_name": "v9.9.9"}""", current, emptyList()))
    }

    @Test
    fun missingVersionCodeFallsBackToCurrentPlusOneAndTitleToTag() {
        val json = """
            {"tag_name": "v0.5.0", "body": "", "assets": [
              {"name": "a.apk", "browser_download_url": "https://example.invalid/a.apk"}]}
        """.trimIndent()

        val update = GitHubReleaseParser.parse(json, current, emptyList())

        assertEquals(181, update?.versionCode)
        assertEquals("v0.5.0", update?.releaseTitle)
        assertEquals(0L, update?.apkSizeBytes)
    }
}

class GitHubReleaseAvailabilityTest {
    private val current = CurrentAppVersion(name = "0.4.1", code = 180)

    private fun release(tag: String, body: String = "Version code: 190", assets: String = "") =
        """{"tag_name": "$tag", "name": "Title", "body": "$body", "html_url": "https://example.invalid/release",
            "published_at": "2026-10-01T00:00:00Z", "assets": [$assets]}"""

    @Test
    fun newerReleaseIsOfferedByItsPageEvenWithoutAnApkAsset() {
        val update = GitHubReleaseParser.parseAvailability(release("v0.5.0"), current)

        assertEquals("0.5.0", update?.versionName)
        assertEquals(190, update?.versionCode)
        assertEquals("https://example.invalid/release", update?.downloadUrl)
        assertEquals(0L, update?.apkSizeBytes)
    }

    @Test
    fun olderOrEqualReleaseIsNotOffered() {
        assertNull(GitHubReleaseParser.parseAvailability(release("v0.4.1", "Version code: 180"), current))
        assertNull(GitHubReleaseParser.parseAvailability(release("v0.3.0", "Version code: 100"), current))
    }

    @Test
    fun malformedBodyOrMissingPageYieldsNothing() {
        assertNull(GitHubReleaseParser.parseAvailability("not json", current))
        assertNull(GitHubReleaseParser.parseAvailability("""{"tag_name": "v9.0.0", "assets": []}""", current))
    }
}

class GitHubNightlyChannelTest {
    private val current = CurrentAppVersion(name = "0.5.0", code = 578)
    private val apk = """[{"name": "SKIPI-universal.apk", "browser_download_url": "https://example.invalid/u.apk", "size": 10}]"""

    private fun release(
        tag: String,
        code: Int?,
        prerelease: Boolean = true,
        draft: Boolean = false,
        assets: String = apk,
    ) = """{"tag_name": "$tag", "name": "R $tag", "draft": $draft, "prerelease": $prerelease,
            "body": "${code?.let { "Version code: $it" }.orEmpty()}", "html_url": "https://example.invalid/$tag",
            "published_at": "2026-10-01T00:00:00Z", "assets": $assets}"""

    private fun list(vararg releases: String) = releases.joinToString(prefix = "[", postfix = "]", separator = ",")

    @Test
    fun nightlyOffersTheNewestPrereleaseThatStableNeverSees() {
        val body = list(release("v0.5.1-rc.2", 590), release("v0.5.1-rc.1", 585), release("v0.5.0", 578, prerelease = false))

        val update = GitHubReleaseParser.parse(body, current, emptyList(), AppUpdateChannel.Nightly)

        assertEquals("0.5.1-rc.2", update?.versionName)
        assertEquals(590, update?.versionCode)
        assertEquals("https://example.invalid/u.apk", update?.downloadUrl)
    }

    @Test
    fun theNewestReleaseWinsEvenWhenGitHubListsItLater() {
        val body = list(release("v0.5.1-rc.1", 585), release("v0.5.1-rc.3", 600), release("v0.5.1-rc.2", 590))

        assertEquals(
            "0.5.1-rc.3",
            GitHubReleaseParser.parse(body, current, emptyList(), AppUpdateChannel.Nightly)?.versionName,
        )
    }

    @Test
    fun aStableReleaseCanBeNewerThanThePrereleasesBeforeIt() {
        val body = list(release("v0.5.1", 620, prerelease = false), release("v0.5.1-rc.3", 600))

        assertEquals(
            "0.5.1",
            GitHubReleaseParser.parse(body, current, emptyList(), AppUpdateChannel.Nightly)?.versionName,
        )
    }

    @Test
    fun draftsAndReleasesWithoutAnApkAreSkipped() {
        val body = list(
            release("v0.6.0-rc.1", 700, draft = true),
            release("v0.5.9-rc.1", 650, assets = "[]"),
            release("v0.5.1-rc.1", 585),
        )

        assertEquals(
            "0.5.1-rc.1",
            GitHubReleaseParser.parse(body, current, emptyList(), AppUpdateChannel.Nightly)?.versionName,
        )
    }

    @Test
    fun nothingNewerOrAnUnusableBodyYieldsNoUpdate() {
        assertNull(GitHubReleaseParser.parse(list(release("v0.5.0", 578, prerelease = false)), current, emptyList(), AppUpdateChannel.Nightly))
        assertNull(GitHubReleaseParser.parse("[]", current, emptyList(), AppUpdateChannel.Nightly))
        assertNull(GitHubReleaseParser.parse("not json", current, emptyList(), AppUpdateChannel.Nightly))
        assertNull(GitHubReleaseParser.parse(release("v0.6.0", 700), current, emptyList(), AppUpdateChannel.Nightly))
    }

    @Test
    fun aPrereleaseWithoutACodeIsNotNewerThanTheSameStableVersion() {
        val body = list(release("v0.5.0-rc.6", null))

        assertNull(GitHubReleaseParser.parse(body, current, emptyList(), AppUpdateChannel.Nightly))
    }

    @Test
    fun aReleaseBodyHasTheShapeOfItsChannel() {
        val one = release("v0.6.0", 700, prerelease = false)
        assertTrue(GitHubReleaseParser.isReleaseBody(one, AppUpdateChannel.Stable))
        assertTrue(GitHubReleaseParser.isReleaseBody(list(one), AppUpdateChannel.Nightly))
        assertTrue(GitHubReleaseParser.isReleaseBody("[]", AppUpdateChannel.Nightly))
        assertFalse(GitHubReleaseParser.isReleaseBody(list(one), AppUpdateChannel.Stable))
        assertFalse(GitHubReleaseParser.isReleaseBody(one, AppUpdateChannel.Nightly))
        assertFalse(GitHubReleaseParser.isReleaseBody("""{"message": "rate limited"}""", AppUpdateChannel.Stable))
        assertFalse(GitHubReleaseParser.isReleaseBody("<html>", AppUpdateChannel.Nightly))
    }

    @Test
    fun stableKeepsReadingASingleLatestRelease() {
        val update = GitHubReleaseParser.parse(release("v0.6.0", 700, prerelease = false), current, emptyList(), AppUpdateChannel.Stable)

        assertEquals("0.6.0", update?.versionName)
        assertNull(GitHubReleaseParser.parse(list(release("v0.6.0", 700)), current, emptyList(), AppUpdateChannel.Stable))
    }

    @Test
    fun availabilityOnDesktopFollowsTheSameChannelRules() {
        val body = list(release("v0.5.1-rc.2", 590, assets = "[]"), release("v0.5.1-rc.1", 585, assets = "[]"))

        val update = GitHubReleaseParser.parseAvailability(body, current, AppUpdateChannel.Nightly)

        assertEquals("0.5.1-rc.2", update?.versionName)
        assertEquals("https://example.invalid/v0.5.1-rc.2", update?.downloadUrl)
        assertNull(GitHubReleaseParser.parseAvailability(body, current, AppUpdateChannel.Stable))
    }
}

class GitHubDesktopReleaseTest {
    private val current = CurrentAppVersion(name = "0.4.1", code = 180)

    private fun asset(name: String, size: Long = 100, digest: String? = null) =
        """{"name": "$name", "browser_download_url": "https://example.invalid/dl/$name", "size": $size""" +
            (digest?.let { """, "digest": "$it"""" } ?: "") + "}"

    private fun release(tag: String, vararg assets: String, code: Int = 190) =
        """{"tag_name": "$tag", "name": "Title", "body": "Version code: $code", "html_url": "https://example.invalid/page/$tag",
            "published_at": "2026-10-01T00:00:00Z", "assets": [${assets.joinToString(",")}]}"""

    @Test
    fun theInstallerOfTheRunningBuildIsOfferedWithItsSizeAndHash() {
        val body = release(
            "v0.5.0",
            asset("SKIPI-universal.apk"),
            asset("SKIPI-windows-x64.msi", size = 1234, digest = "sha256:ABCDEF"),
            asset("SKIPI-windows-x64-portable.zip"),
        )

        val update = GitHubReleaseParser.parseAvailability(body, current, desktopPackage = DesktopUpdatePackage.WindowsMsi)

        assertEquals("0.5.0", update?.versionName)
        assertEquals("https://example.invalid/dl/SKIPI-windows-x64.msi", update?.downloadUrl)
        assertEquals("SKIPI-windows-x64.msi", update?.assetName)
        assertEquals(1234L, update?.apkSizeBytes)
        assertEquals("sha256:ABCDEF", update?.assetSha256)
    }

    @Test
    fun aPortableBuildGetsThePortableArchive() {
        val body = release("v0.5.0", asset("SKIPI-windows-x64.msi"), asset("SKIPI-windows-x64-portable.zip"))

        val update = GitHubReleaseParser.parseAvailability(body, current, desktopPackage = DesktopUpdatePackage.WindowsPortable)

        assertEquals("SKIPI-windows-x64-portable.zip", update?.assetName)
    }

    @Test
    fun aReleaseWithoutThatInstallerIsStillOfferedByItsPage() {
        // The Desktop jobs upload after the APK job, so a release can be public before its installers are.
        val body = release("v0.5.0", asset("SKIPI-universal.apk"), asset("SKIPI-linux-x64.deb"))

        val update = GitHubReleaseParser.parseAvailability(body, current, desktopPackage = DesktopUpdatePackage.WindowsMsi)

        assertEquals("https://example.invalid/page/v0.5.0", update?.downloadUrl)
        assertEquals("", update?.assetName)
        assertEquals(false, update?.canDownload)
    }

    @Test
    fun onlyAnExactAssetNameMatches() {
        val body = release(
            "v0.5.0",
            asset("SKIPI-windows-x64.msi.sha256"),
            asset("old-SKIPI-windows-x64.msi"),
            asset("skipi-windows-x64.MSI"),
        )

        val update = GitHubReleaseParser.parseAvailability(body, current, desktopPackage = DesktopUpdatePackage.WindowsMsi)

        assertEquals("", update?.assetName)
    }

    @Test
    fun withoutAnInstallerKindTheOfferStaysAPageLink() {
        val body = release("v0.5.0", asset("SKIPI-windows-x64.msi"))

        val update = GitHubReleaseParser.parseAvailability(body, current)

        assertEquals("https://example.invalid/page/v0.5.0", update?.downloadUrl)
        assertEquals("", update?.assetName)
    }

    @Test
    fun nightlyOffersTheNewestReleaseWithItsOwnInstaller() {
        val body = "[" + listOf(
            release("v0.5.1-rc.1", asset("SKIPI-linux-x64.AppImage"), code = 585),
            release("v0.5.1-rc.2", asset("SKIPI-linux-x64.AppImage"), code = 590),
        ).joinToString(",") + "]"

        val update = GitHubReleaseParser.parseAvailability(
            body, current, AppUpdateChannel.Nightly, DesktopUpdatePackage.LinuxAppImage,
        )

        assertEquals("0.5.1-rc.2", update?.versionName)
        assertEquals("https://example.invalid/dl/SKIPI-linux-x64.AppImage", update?.downloadUrl)
    }

    @Test
    fun everyInstallerHasItsOwnReleaseAsset() {
        val names = DesktopUpdatePackage.entries.map(DesktopUpdatePackage::assetName)

        assertEquals(names.size, names.toSet().size)
        assertEquals("SKIPI-macos-arm64.dmg", DesktopUpdatePackage.MacDmg.assetName)
    }
}
