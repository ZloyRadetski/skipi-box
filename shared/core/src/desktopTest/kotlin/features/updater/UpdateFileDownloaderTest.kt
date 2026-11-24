// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class UpdateFileDownloaderTest {
    private val payload = ByteArray(300_000) { (it % 251).toByte() }
    private val directory: File = Files.createTempDirectory("skipi-shared-update-").toFile()
    private val requested = mutableListOf<Pair<String, Long>>()

    @AfterTest
    fun cleanup() {
        directory.deleteRecursively()
    }

    private class FakeResponse(
        override val status: Int,
        override val url: String,
        private val bytes: ByteArray = ByteArray(0),
        private val headers: Map<String, String> = emptyMap(),
        private val announced: Long = bytes.size.toLong(),
    ) : UpdateResponse {
        override val contentLength: Long get() = announced
        override fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        override val body: InputStream get() = ByteArrayInputStream(bytes)
        override fun close() = Unit
    }

    private fun transport(handler: (url: String, rangeFrom: Long) -> UpdateResponse) = object : UpdateTransport {
        override fun open(url: String, rangeFrom: Long, headers: Map<String, String>): UpdateResponse {
            requested += url to rangeFrom
            return handler(url, rangeFrom)
        }
    }

    private fun serving(bytes: ByteArray = payload) = transport { url, _ -> FakeResponse(200, url, bytes) }

    private fun sha256(bytes: ByteArray) = "sha256:" + bytesToHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun offer(
        url: String = "https://github.com/o/r/releases/download/v1/SKIPI.apk",
        name: String = "SKIPI.apk",
        size: Long = payload.size.toLong(),
        digest: String? = sha256(payload),
    ) = AppUpdateInfo(
        versionName = "0.5.0", versionCode = 190, releaseTitle = "t", changelog = "",
        downloadUrl = url, assetName = name, apkSizeBytes = size, publishedAt = "", assetSha256 = digest,
    )

    private fun run(
        transport: UpdateTransport,
        update: AppUpdateInfo = offer(),
        resume: Boolean = true,
        isTrusted: (URI) -> Boolean = ::isTrustedReleaseHost,
    ): Result<List<AppUpdateDownloadProgress>> = runCatching {
        runBlocking { UpdateFileDownloader(transport, isTrusted).download(update, directory, resume = resume).toList() }
    }

    private fun filesLeft() = directory.list().orEmpty().sorted()

    @Test
    fun theFileIsDownloadedVerifiedAndPromoted() {
        val items = run(serving()).getOrThrow()

        val done = items.last() as AppUpdateDownloadProgress.Completed
        assertTrue(payload.contentEquals(File(done.apkFilePath).readBytes()))
        assertEquals(listOf("SKIPI.apk"), filesLeft())
        val bytes = items.filterIsInstance<AppUpdateDownloadProgress.Downloading>().map { it.downloadedBytes }
        assertEquals(bytes.sorted(), bytes)
        assertEquals(payload.size.toLong(), bytes.last())
    }

    @Test
    fun aRedirectToANonTrustedHostIsRejectedBeforeItIsContacted() {
        val t = transport { url, _ ->
            if ("github.com" in url) {
                FakeResponse(302, url, headers = mapOf("Location" to "https://evil.example/SKIPI.apk"))
            } else {
                FakeResponse(200, url, payload)
            }
        }

        assertTrue(run(t).isFailure)
        assertEquals(1, requested.size)
        assertEquals(emptyList(), filesLeft())
    }

    @Test
    fun aRedirectFromHttpsToHttpIsRejectedEvenForATrustedHost() {
        val t = transport { url, _ ->
            if (url.startsWith("https")) {
                FakeResponse(302, url, headers = mapOf("Location" to "http://github.com/SKIPI.apk"))
            } else {
                FakeResponse(200, url, payload)
            }
        }

        assertTrue(run(t, isTrusted = { true }).isFailure)
        assertEquals(1, requested.size)
    }

    @Test
    fun aRedirectBetweenTrustedHostsIsFollowedAndTheFinalUrlIsChecked() {
        val t = transport { url, _ ->
            if ("github.com" in url) {
                FakeResponse(302, url, headers = mapOf("Location" to "https://objects.githubusercontent.com/x"))
            } else {
                FakeResponse(200, url, payload)
            }
        }
        assertTrue(run(t).isSuccess)
        File(directory, "SKIPI.apk").delete()

        val sneaky = transport { _, _ -> FakeResponse(200, "https://evil.example/x", payload) }
        assertTrue(run(sneaky).isFailure)
    }

    @Test
    fun aLinkThatIsNotHttpsOrNotGitHubIsNeverContacted() {
        assertTrue(run(serving(), offer(url = "http://github.com/a.apk")).isFailure)
        assertTrue(run(serving(), offer(url = "https://example.com/a.apk")).isFailure)
        assertEquals(emptyList(), requested)
    }

    @Test
    fun tooManyRedirectsFail() {
        val t = transport { url, _ -> FakeResponse(302, url, headers = mapOf("Location" to "https://github.com/loop")) }

        assertTrue(run(t).isFailure)
        assertEquals(UpdateHttp.MaxRedirects + 1, requested.size)
    }

    @Test
    fun aMalformedDigestFailsClosedWithoutAnyRequest() {
        listOf("sha256:abc", "nonsense", "sha256:" + "z".repeat(64)).forEach { digest ->
            assertTrue(run(serving(), offer(digest = digest)).isFailure, digest)
        }
        assertEquals(emptyList(), requested)
        assertEquals(emptyList(), filesLeft())
    }

    @Test
    fun aMismatchingDigestFailsAndDeletesTheFile() {
        val result = run(serving(), offer(digest = sha256(ByteArray(3))))

        assertTrue(result.isFailure)
        assertEquals(emptyList(), filesLeft())
    }

    @Test
    fun aMissingDigestIsAllowedWhenTheSizeIsKnown() {
        assertTrue(run(serving(), offer(digest = null)).isSuccess)
    }

    @Test
    fun aSizeMismatchFails() {
        assertTrue(run(serving(), offer(size = payload.size + 1L, digest = null)).isFailure)
        assertTrue(run(serving(), offer(size = payload.size - 1L, digest = null)).isFailure)
    }

    @Test
    fun unsafeAssetNamesAreRejected() {
        listOf("../evil.apk", "a/b.apk", "a\\b.apk", "..", ".", "C:evil.apk", "a\u0000b").forEach { name ->
            assertTrue(run(serving(), offer(name = name)).isFailure, name)
        }
        assertEquals(emptyList(), requested)
    }

    @Test
    fun anInterruptedDownloadResumesWithARangeRequest() {
        val half = payload.size / 2
        File(directory, "SKIPI.apk.part").writeBytes(payload.copyOf(half))
        val t = transport { url, from ->
            assertEquals(half.toLong(), from)
            FakeResponse(
                206, url, payload.copyOfRange(half, payload.size),
                mapOf("Content-Range" to "bytes $half-${payload.size - 1}/${payload.size}"),
            )
        }

        val items = run(t).getOrThrow()

        assertEquals(listOf("https://github.com/o/r/releases/download/v1/SKIPI.apk" to half.toLong()), requested)
        val done = items.last() as AppUpdateDownloadProgress.Completed
        assertTrue(payload.contentEquals(File(done.apkFilePath).readBytes()))
    }

    @Test
    fun aServerThatIgnoresTheRangeRestartsTheFile() {
        File(directory, "SKIPI.apk.part").writeBytes(payload.copyOf(1000))

        val items = run(serving()).getOrThrow()

        assertTrue(payload.contentEquals(File((items.last() as AppUpdateDownloadProgress.Completed).apkFilePath).readBytes()))
    }

    @Test
    fun aFailedTransferKeepsThePartOnlyWhenResuming() {
        val cut = transport { url, _ -> FakeResponse(200, url, payload.copyOf(1000), announced = payload.size.toLong()) }

        assertTrue(run(cut, resume = true).isFailure)
        assertEquals(listOf("SKIPI.apk.part"), filesLeft())
        assertTrue(run(cut, resume = false).isFailure)
        assertEquals(emptyList(), filesLeft())
    }

    @Test
    fun aValidFileAlreadyOnDiskIsNotFetchedAgain() {
        File(directory, "SKIPI.apk").writeBytes(payload)

        val items = run(serving()).getOrThrow()

        assertEquals(emptyList(), requested)
        assertTrue(items.last() is AppUpdateDownloadProgress.Completed)
    }

    @Test
    fun aDamagedFileAlreadyOnDiskIsReplaced() {
        File(directory, "SKIPI.apk").writeBytes(ByteArray(10))

        assertTrue(run(serving()).isSuccess)
        assertTrue(payload.contentEquals(File(directory, "SKIPI.apk").readBytes()))
    }

    @Test
    fun aFailedStatusIsAFailure() {
        assertTrue(run(transport { url, _ -> FakeResponse(404, url) }).isFailure)
    }

    @Test
    fun theHelpersNormalizeAndRejectNames() {
        val hex = "ab".repeat(32)
        assertEquals(hex, normalizeSha256("sha256:${hex.uppercase()}"))
        assertEquals(hex, normalizeSha256(" $hex "))
        assertNull(normalizeSha256("sha256:abc"))
        assertNull(normalizeSha256(null))
        assertEquals("00ff10", bytesToHex(byteArrayOf(0, -1, 16)))
        assertTrue(isSafeAssetName("SKIPI-windows-x64.msi"))
        assertFalse(isSafeAssetName(""))
    }

    @Test
    fun theReleaseHostsAreTrustedAndNoOthers() {
        fun trusted(url: String) = isTrustedReleaseHost(URI(url))

        assertTrue(trusted("https://github.com/Radetski/skipi/releases/download/v1/a.msi"))
        assertTrue(trusted("https://objects.githubusercontent.com/x"))
        assertTrue(trusted("https://release-assets.githubusercontent.com/x"))
        assertFalse(trusted("http://github.com/x"))
        assertFalse(trusted("https://github.com.evil.example/x"))
        assertFalse(trusted("https://evilgithubusercontent.com/x"))
        assertFalse(trusted("https://example.com/x"))
    }

    @Test
    fun theFetcherSendsTheEtagAndMapsNotModified() {
        val seen = mutableListOf<Map<String, String>>()
        val t = object : UpdateTransport {
            override fun open(url: String, rangeFrom: Long, headers: Map<String, String>): UpdateResponse {
                seen += headers
                return if ("If-None-Match" in headers) {
                    FakeResponse(304, url)
                } else {
                    FakeResponse(200, url, "[]".toByteArray(), mapOf("ETag" to "\"v1\""))
                }
            }
        }
        val fetcher = GitHubUpdateFetcher(t, "0.5.0")

        assertEquals(ReleaseListResponse.Ok("[]", "\"v1\""), fetcher.fetchReleases(AppUpdateChannel.Stable))
        assertEquals(ReleaseListResponse.NotModified, fetcher.fetchReleases(AppUpdateChannel.Stable, "\"v1\""))
        assertEquals("SKIPI-App/0.5.0", seen.first()["User-Agent"])
        assertEquals(UpdateHttp.ApiAccept, seen.first()["Accept"])
        assertNull(GitHubUpdateFetcher(transport { url, _ -> FakeResponse(404, url) }, "0.5.0").fetchManifest(AppUpdateChannel.Stable))
        assertTrue(runCatching {
            GitHubUpdateFetcher(transport { url, _ -> FakeResponse(500, url) }, "0.5.0").fetchReleases(AppUpdateChannel.Stable)
        }.exceptionOrNull() is IOException)
    }
}
