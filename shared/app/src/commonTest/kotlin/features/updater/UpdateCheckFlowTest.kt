// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class UpdateCheckFlowTest {
    private val offer = AppUpdateInfo(
        versionName = "0.5.1", versionCode = 51, releaseTitle = "t", changelog = "c",
        downloadUrl = "u", assetName = "a", apkSizeBytes = 1, publishedAt = "p",
    )
    private val manifest = UpdateManifest(schema = 1, versionName = "0.5.1", versionCode = 51, tag = "v0.5.1")

    private suspend fun run(
        manifestBody: suspend () -> String? = { null },
        manifestOffer: (UpdateManifest) -> AppUpdateInfo? = { null },
        releases: suspend () -> ReleaseListResponse = { ReleaseListResponse.Failed },
        releaseOffer: (String) -> AppUpdateInfo? = { null },
        parseManifest: (String) -> UpdateManifest? = { manifest },
        isReleaseBody: (String) -> Boolean = { true },
    ) = runUpdateCheck(
        fetchManifest = manifestBody,
        parseManifest = parseManifest,
        manifestOffer = manifestOffer,
        fetchReleases = releases,
        isReleaseBody = isReleaseBody,
        releaseOffer = releaseOffer,
    )

    @Test
    fun aUsableManifestAnswersWithoutTheReleaseList() = runTest {
        var asked = false
        val result = run(
            manifestBody = { "m" },
            manifestOffer = { offer },
            releases = { asked = true; ReleaseListResponse.Failed },
        )
        assertEquals(UpdateCheckResult.Success(offer, eTag = null), result)
        assertEquals(false, asked)
    }

    @Test
    fun aManifestWithNothingNewIsUpToDateNotAFallback() = runTest {
        var asked = false
        val result = run(manifestBody = { "m" }, manifestOffer = { null }, releases = { asked = true; ReleaseListResponse.Failed })
        assertEquals(UpdateCheckResult.Success(null, null), result)
        assertEquals(false, asked)
    }

    @Test
    fun noManifestFallsBackToTheReleaseList() = runTest {
        val result = run(
            manifestBody = { null },
            releases = { ReleaseListResponse.Ok("[]", eTag = "W/1") },
            releaseOffer = { offer },
        )
        assertEquals(UpdateCheckResult.Success(offer, eTag = "W/1"), result)
    }

    @Test
    fun anUnreadableManifestFallsBackToo() = runTest {
        val result = run(
            manifestBody = { "garbage" },
            parseManifest = { null },
            releases = { ReleaseListResponse.Ok("[]", null) },
            releaseOffer = { offer },
        )
        assertEquals(UpdateCheckResult.Success(offer, null), result)
    }

    @Test
    fun aFailingManifestFetchFallsBack() = runTest {
        val result = run(
            manifestBody = { error("offline") },
            releases = { ReleaseListResponse.Ok("[]", null) },
            releaseOffer = { null },
        )
        assertEquals(UpdateCheckResult.Success(null, null), result)
    }

    @Test
    fun notModifiedPassesThrough() = runTest {
        assertEquals(UpdateCheckResult.NotModified, run(releases = { ReleaseListResponse.NotModified }))
    }

    @Test
    fun aFailedOrForeignReleaseListIsAFailureNeverUpToDate() = runTest {
        assertEquals(UpdateCheckResult.Failed, run(releases = { ReleaseListResponse.Failed }))
        assertEquals(UpdateCheckResult.Failed, run(releases = { ReleaseListResponse.Ok("<html>", null) }, isReleaseBody = { false }))
        assertEquals(UpdateCheckResult.Failed, run(releases = { error("boom") }))
    }

    @Test
    fun cancellationIsNotSwallowed() = runTest {
        assertFailsWith<CancellationException> { run(manifestBody = { throw CancellationException("stop") }) }
        assertFailsWith<CancellationException> { run(releases = { throw CancellationException("stop") }) }
    }
}
