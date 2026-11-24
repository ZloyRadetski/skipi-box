// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Downloads the release file of an update and proves it is the published one. The shared rules of both clients live
 * here: HTTPS only, a trusted host at every redirect hop and on the final URL, a `.part` file that is promoted
 * atomically, Range resume, the size check and a SHA-256 check that fails closed (a digest that is present but
 * malformed or different is an error, never a skip).
 *
 * [isTrusted] decides which hosts may serve the file; [requireHttps] is only turned off by tests with a local server.
 */
class UpdateFileDownloader(
    private val transport: UpdateTransport,
    private val isTrusted: (URI) -> Boolean = ::isTrustedReleaseHost,
    private val requireHttps: Boolean = true,
) {
    /**
     * Emits [AppUpdateDownloadProgress.Downloading] items and, last, [AppUpdateDownloadProgress.Completed]; every
     * failure is thrown to the collector (cancellation included). With [resume] the `.part` file survives a failed or
     * cancelled transfer and the next call continues it with a Range request; without it a partial file is never
     * kept. A file that fails its size or SHA-256 check is always deleted.
     */
    fun download(
        update: AppUpdateInfo,
        directory: File,
        targetName: String = update.assetName,
        resume: Boolean = true,
    ): Flow<AppUpdateDownloadProgress> = flow {
        require(update.canDownload) { "The release has no file for this installation" }
        require(isSafeAssetName(update.assetName) && isSafeAssetName(targetName)) { "Unsafe update file name" }
        val expectedHash = when {
            update.assetSha256.isNullOrBlank() -> null
            else -> normalizeSha256(update.assetSha256) ?: throw IOException("The published SHA-256 of the update is malformed")
        }
        val expectedSize = update.apkSizeBytes.takeIf { it > 0L }
        // A link that is not allowed is refused before anything is read or written.
        val source = try {
            URI(update.downloadUrl)
        } catch (error: Exception) {
            throw IOException("Invalid update link", error)
        }
        if (!allowed(source)) throw IOException("The update link is not a GitHub release link")

        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Could not create the update directory")
        val target = File(directory, targetName)
        val partial = File(directory, "$targetName.part")

        if (target.isFile && isComplete(target, expectedSize, expectedHash)) {
            emit(AppUpdateDownloadProgress.Downloading(1f, target.length(), target.length()))
            emit(AppUpdateDownloadProgress.Completed(target.absolutePath))
            return@flow
        }
        if (target.exists() && !target.delete()) throw IOException("Could not replace the previous update file")

        if (!resume) partial.delete()
        // A finished part can be left behind if the process dies between the last write and the rename.
        if (partial.isFile && isComplete(partial, expectedSize, expectedHash)) {
            promote(partial, target)
            emit(AppUpdateDownloadProgress.Completed(target.absolutePath))
            return@flow
        }
        var retained = partial.takeIf(File::isFile)?.length() ?: 0L
        if (expectedSize != null && retained >= expectedSize) {
            if (!partial.delete()) throw IOException("Could not reset the partial update file")
            retained = 0L
        }

        var keepPartial = resume
        try {
            emit(downloading(retained, expectedSize ?: 0L))
            val response = transport.openFollowing(
                url = update.downloadUrl,
                rangeFrom = retained,
                headers = mapOf("User-Agent" to UpdateHttp.UserAgent),
                allowed = ::allowed,
            )
            response.use {
                if (response.status != 200 && response.status != 206) throw IOException("HTTP ${response.status}")
                val append = retained > 0L && response.status == 206
                if (append) {
                    val start = response.header("Content-Range")?.let(::rangeStart)
                    if (start != null && start != retained) throw IOException("The server resumed at the wrong position")
                } else if (retained > 0L) {
                    if (!partial.delete()) throw IOException("Could not restart the partial update file")
                    retained = 0L
                }
                val total = totalBytes(response, retained, append) ?: expectedSize ?: 0L

                var written = retained
                var reported = written
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(BufferSize)
                    val input = response.body
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (shouldReport(written, reported, total)) {
                            reported = written
                            emit(downloading(written, total))
                        }
                    }
                    output.fd.sync()
                }
                if (written != reported) emit(downloading(written, total))
                val finalBytes = partial.length()
                if (expectedSize != null && finalBytes != expectedSize) {
                    if (expectedSize < finalBytes) keepPartial = false
                    throw IOException("The update has the wrong size ($finalBytes of $expectedSize bytes)")
                }
                if (total > 0L && finalBytes != total) {
                    throw IOException("Incomplete update download: $finalBytes of $total bytes")
                }
            }
            if (expectedHash != null && sha256Hex(partial) != expectedHash) {
                keepPartial = false
                throw IOException("The update does not match the published SHA-256")
            }
            promote(partial, target)
            emit(AppUpdateDownloadProgress.Completed(target.absolutePath))
        } catch (error: Throwable) {
            if (!keepPartial) partial.delete()
            throw error
        }
    }.flowOn(Dispatchers.IO)

    private fun allowed(uri: URI): Boolean =
        (!requireHttps || uri.scheme.equals("https", ignoreCase = true)) && isTrusted(uri)

    private fun downloading(done: Long, total: Long) = AppUpdateDownloadProgress.Downloading(
        progress = if (total > 0L) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f,
        downloadedBytes = done,
        totalBytes = total,
    )

    /** Progress is throttled: a 2 % step when the total is known, else every 128 KiB; the last bytes always count. */
    private fun shouldReport(written: Long, reported: Long, total: Long): Boolean =
        if (total > 0L) {
            written >= total || (written - reported).toDouble() / total >= ProgressStep
        } else {
            written - reported >= ReportEveryBytes
        }

    private fun totalBytes(response: UpdateResponse, retained: Long, append: Boolean): Long? {
        response.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
        val length = response.contentLength.takeIf { it > 0L } ?: return null
        return if (append) retained + length else length
    }

    private fun rangeStart(contentRange: String): Long? =
        contentRange.substringAfter("bytes ", "").substringBefore('-').trim().toLongOrNull()

    private fun isComplete(file: File, expectedSize: Long?, expectedHash: String?): Boolean {
        if (expectedSize == null && expectedHash == null) return false
        if (expectedSize != null && file.length() != expectedSize) return false
        return expectedHash == null || sha256Hex(file) == expectedHash
    }

    private fun promote(partial: File, target: File) {
        try {
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val BufferSize = 64 * 1024
        const val ProgressStep = 0.02
        const val ReportEveryBytes = 128L * 1024L
    }
}

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return bytesToHex(digest.digest())
}
