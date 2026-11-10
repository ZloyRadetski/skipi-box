// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import features.updater.AppUpdateInfo
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DesktopUpdateDownloaderTest {
    private val payload = ByteArray(300_000) { (it % 251).toByte() }
    private val servers = mutableListOf<HttpServer>()
    private val directory: Path = Files.createTempDirectory("skipi-update-")
    private val requests = AtomicInteger()

    @AfterTest
    fun cleanup() {
        servers.forEach { it.stop(0) }
        directory.toFile().deleteRecursively()
    }

    private fun server(handler: (HttpExchange) -> Unit): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { created ->
            created.createContext("/") { exchange ->
                requests.incrementAndGet()
                handler(exchange)
                exchange.close()
            }
            created.start()
            servers += created
        }

    private fun serving(bytes: ByteArray): HttpServer = server { exchange ->
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    private fun sha256(bytes: ByteArray) = "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun offer(
        server: HttpServer,
        name: String = "SKIPI-windows-x64.msi",
        size: Long = payload.size.toLong(),
        digest: String? = sha256(payload),
    ) = AppUpdateInfo(
        versionName = "0.5.0", versionCode = 190, releaseTitle = "t", changelog = "",
        downloadUrl = "http://127.0.0.1:${server.address.port}/file", assetName = name,
        apkSizeBytes = size, publishedAt = "", assetSha256 = digest,
    )

    private fun downloader(allowed: (URI) -> Boolean = { true }) =
        DesktopUpdateDownloader(isTrusted = allowed, requireHttps = false)

    @Test
    fun theFileIsDownloadedCheckedAndKeptUnderItsName() = runBlocking {
        val server = serving(payload)
        val progress = mutableListOf<Long>()

        val result = downloader().download(offer(server), directory) { done, _ -> progress += done }

        val file = result.getOrThrow()
        assertEquals("SKIPI-windows-x64.msi", file.fileName.toString())
        assertTrue(payload.contentEquals(file.readBytes()))
        assertEquals(payload.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress)
        assertEquals(listOf("SKIPI-windows-x64.msi"), directory.listDirectoryEntries().map { it.fileName.toString() })
    }

    @Test
    fun aFileWithTheWrongHashIsRejectedAndNothingIsLeftBehind() = runBlocking {
        val server = serving(payload)

        val result = downloader().download(offer(server, digest = sha256(ByteArray(3))), directory) { _, _ -> }

        assertTrue(result.isFailure)
        assertTrue(directory.listDirectoryEntries().isEmpty())
    }

    @Test
    fun aFileOfTheWrongSizeIsRejected() = runBlocking {
        val server = serving(payload)

        val result = downloader().download(offer(server, size = payload.size + 1L), directory) { _, _ -> }

        assertTrue(result.isFailure)
        assertTrue(directory.listDirectoryEntries().isEmpty())
    }

    @Test
    fun aFailedStatusIsAFailure() = runBlocking {
        val server = server { exchange -> exchange.sendResponseHeaders(404, -1) }

        assertTrue(downloader().download(offer(server), directory) { _, _ -> }.isFailure)
    }

    @Test
    fun anUntrustedHostIsNeverContacted() = runBlocking {
        val server = serving(payload)

        val result = downloader { false }.download(offer(server), directory) { _, _ -> }

        assertTrue(result.isFailure)
        assertEquals(0, requests.get())
    }

    @Test
    fun aRedirectToAnUntrustedHostIsRefused() = runBlocking {
        val target = serving(payload)
        val redirector = server { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${target.address.port}/file")
            exchange.sendResponseHeaders(302, -1)
        }

        val result = downloader { uri -> uri.port == redirector.address.port }
            .download(offer(redirector), directory) { _, _ -> }

        assertTrue(result.isFailure)
        assertTrue(directory.listDirectoryEntries().isEmpty())
    }

    @Test
    fun aFileThatIsAlreadyDownloadedAndValidIsNotFetchedAgain() = runBlocking {
        val server = serving(payload)
        val first = downloader().download(offer(server), directory) { _, _ -> }.getOrThrow()
        val before = requests.get()

        val second = downloader().download(offer(server), directory) { _, _ -> }.getOrThrow()

        assertEquals(first, second)
        assertEquals(before, requests.get())
    }

    @Test
    fun aDamagedFileFromAnEarlierRunIsDownloadedAgain() = runBlocking {
        val server = serving(payload)
        Files.write(directory.resolve("SKIPI-windows-x64.msi"), ByteArray(10))

        val file = downloader().download(offer(server), directory) { _, _ -> }.getOrThrow()

        assertTrue(payload.contentEquals(file.readBytes()))
    }

    @Test
    fun aNameThatLeavesTheFolderIsRefused() = runBlocking {
        val server = serving(payload)

        listOf("../evil.msi", "a/b.msi", "a\\b.msi", "..").forEach { name ->
            val result = downloader().download(offer(server, name = name), directory) { _, _ -> }
            assertTrue(result.isFailure, name)
        }
        assertFalse(directory.resolveSibling("evil.msi").toFile().exists())
        assertEquals(0, requests.get())
    }

    @Test
    fun anOfferThatIsOnlyAPageLinkCannotBeDownloaded() = runBlocking {
        val server = serving(payload)

        assertTrue(downloader().download(offer(server, name = ""), directory) { _, _ -> }.isFailure)
        assertEquals(0, requests.get())
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
}
