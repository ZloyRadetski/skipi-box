// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.updater.AppUpdateInfo
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Release files come from GitHub over HTTPS, and a redirect may only lead to GitHub's own asset hosts. */
internal fun isTrustedReleaseHost(uri: URI): Boolean {
    if (!uri.scheme.equals("https", ignoreCase = true)) return false
    val host = uri.host?.lowercase() ?: return false
    return host == "github.com" || host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")
}

/**
 * Downloads the release file of an update into a folder and proves it is the published one: its size and
 * the SHA-256 GitHub lists for the asset must match before it is kept. A file that fails is deleted, so
 * nothing half-written or altered is ever offered to the installer.
 */
internal class DesktopUpdateDownloader(
    private val isTrusted: (URI) -> Boolean = ::isTrustedReleaseHost,
    private val requireHttps: Boolean = true,
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build(),
) {
    /** [onProgress] gets the bytes written so far and the expected total (0 when unknown). */
    suspend fun download(
        update: AppUpdateInfo,
        directory: Path,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<Path> = withContext(Dispatchers.IO) {
        try {
            Result.success(fetch(update, directory, onProgress))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun fetch(update: AppUpdateInfo, directory: Path, onProgress: (Long, Long) -> Unit): Path {
        require(update.canDownload) { "The release has no file for this installation" }
        val name = update.assetName
        require(name.isNotBlank() && '/' !in name && '\\' !in name && name != "." && name != "..") {
            "Unsafe update file name"
        }
        val source = URI(update.downloadUrl)
        require(!requireHttps || source.scheme.equals("https", ignoreCase = true)) { "The update link is not HTTPS" }
        require(isTrusted(source)) { "The update link is not a GitHub release link" }

        Files.createDirectories(directory)
        val target = directory.resolve(name)
        val expectedHash = update.assetSha256?.substringAfter(':', "")?.lowercase()?.takeIf(String::isNotBlank)
        if (Files.isRegularFile(target) && matches(target, update.apkSizeBytes, expectedHash)) {
            val size = Files.size(target)
            onProgress(size, size)
            return target
        }
        Files.deleteIfExists(target)

        val partial = directory.resolve("$name.part")
        try {
            val request = HttpRequest.newBuilder(source)
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "SKIPI-App")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
            response.body().use { input ->
                if (response.statusCode() != 200) throw IOException("HTTP ${response.statusCode()}")
                check(isTrusted(response.uri())) { "The update was redirected away from GitHub" }
                val total = update.apkSizeBytes.takeIf { it > 0L }
                    ?: response.headers().firstValueAsLong("Content-Length").orElse(0L)
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                var reported = 0L
                Files.newOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        if (written - reported >= ReportEveryBytes) {
                            reported = written
                            onProgress(written, total)
                        }
                    }
                }
                onProgress(written, total.takeIf { it > 0L } ?: written)
                if (update.apkSizeBytes > 0L && written != update.apkSizeBytes) {
                    throw IOException("The update has the wrong size ($written of ${update.apkSizeBytes} bytes)")
                }
                if (expectedHash != null && toHex(digest.digest()) != expectedHash) {
                    throw IOException("The update does not match the published SHA-256")
                }
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
            return target
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    private fun matches(file: Path, expectedSize: Long, expectedHash: String?): Boolean {
        if (expectedSize > 0L && Files.size(file) != expectedSize) return false
        if (expectedHash == null) return expectedSize > 0L
        return toHex(sha256Of(file)) == expectedHash
    }

    private fun sha256Of(file: Path): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun toHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private companion object {
        const val ReportEveryBytes = 128L * 1024L
    }
}

/** Removes the folders of earlier versions' downloads: only the one being installed is kept. */
internal fun pruneOldUpdateDownloads(root: Path, keepFolder: String) {
    if (!Files.isDirectory(root)) return
    Files.list(root).use { entries ->
        entries.filter { it.fileName.toString() != keepFolder }.forEach { it.toFile().deleteRecursively() }
    }
}
