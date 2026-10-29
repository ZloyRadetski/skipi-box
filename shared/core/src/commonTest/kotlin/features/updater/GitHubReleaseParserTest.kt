// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
