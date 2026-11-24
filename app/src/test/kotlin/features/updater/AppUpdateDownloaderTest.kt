// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppUpdateDownloaderTest {
    private val payload = ByteArray(50_000) { (it % 251).toByte() }
    private val directory: File = Files.createTempDirectory("skipi-app-update-").toFile()

    @After
    fun cleanup() {
        directory.deleteRecursively()
    }

    private class Response(override val url: String, private val bytes: ByteArray) : UpdateResponse {
        override val status = 200
        override val contentLength = bytes.size.toLong()
        override fun header(name: String): String? = null
        override val body: InputStream get() = ByteArrayInputStream(bytes)
        override fun close() = Unit
    }

    private fun downloader(bytes: ByteArray = payload) = AppUpdateDownloader(
        directory,
        UpdateFileDownloader(object : UpdateTransport {
            override fun open(url: String, rangeFrom: Long, headers: Map<String, String>): UpdateResponse =
                Response(url, bytes)
        }),
    )

    private fun offer(digest: String?) = AppUpdateInfo(
        versionName = "0.5.0", versionCode = 190, releaseTitle = "t", changelog = "",
        downloadUrl = "https://github.com/o/r/releases/download/v1/SKIPI.apk", assetName = "SKIPI.apk",
        apkSizeBytes = payload.size.toLong(), publishedAt = "", assetSha256 = digest,
    )

    private fun sha256(bytes: ByteArray) = "sha256:" + bytesToHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    @Test
    fun theApkIsKeptUnderItsVersionedName() = runBlocking {
        val items = downloader().downloadApk(offer(sha256(payload))).toList()

        val done = items.last() as AppUpdateDownloadProgress.Completed
        assertEquals(File(directory, "v190-SKIPI.apk").absolutePath, done.apkFilePath)
        assertTrue(payload.contentEquals(File(done.apkFilePath).readBytes()))
    }

    @Test
    fun aMalformedDigestIsAFailedItemNotASkippedCheck() = runBlocking {
        val items = downloader().downloadApk(offer("sha256:abc")).toList()

        assertTrue(items.last() is AppUpdateDownloadProgress.Failed)
        assertEquals(emptyList(), directory.list().orEmpty().toList())
    }
}
