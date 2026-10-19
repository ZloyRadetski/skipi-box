// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import features.resources.ResourceFileDownloadCancelledException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/** A platform connection used by the shared resource-file transfer algorithm. */
interface ResourceFileDownloadConnection {
    val responseCode: Int
    val location: String?
    val contentLength: Long
    val inputStream: InputStream

    fun disconnect()
}

/** Opens one response connection for the current URL hop of an attempt. */
fun interface ResourceFileDownloadConnectionOpener {
    fun open(uri: URI): ResourceFileDownloadConnection
}

/** Creates the opener for a retry attempt. */
fun interface ResourceFileDownloadAttemptFactory {
    fun beginAttempt(): ResourceFileDownloadConnectionOpener
}

/**
 * Executes resource-file HTTP transfers while leaving connection creation,
 * cancellation state, and atomic publication to the platform host.
 */
class ResourceFileDownloadTransfer(
    private val attempts: ResourceFileDownloadAttemptFactory,
    private val publishAtomically: ((OutputStream) -> Unit) -> Unit,
    private val isCancelled: () -> Boolean,
    private val throwIfCancelled: () -> Unit,
    private val waitBeforeRetry: (Long) -> Unit = { delayMillis -> Thread.sleep(delayMillis) },
) {
    fun download(
        url: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) {
        var lastError: Throwable? = null
        repeat(MaxAttempts) { attempt ->
            try {
                downloadWithRedirects(url, onProgress)
                return
            } catch (error: Throwable) {
                if (isCancelled()) {
                    throw ResourceFileDownloadCancelledException()
                }
                if (error is IOException && attempt < MaxAttempts - 1) {
                    lastError = error
                    waitBeforeRetry(RetryBackoffMillis * (1L shl attempt))
                } else {
                    throw error
                }
            }
        }
        throw lastError ?: error("Download failed")
    }

    private fun downloadWithRedirects(
        url: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        // A new opener captures platform attempt state (for example, one VPN
        // network snapshot) once per retry and reuses it for every redirect.
        val opener = attempts.beginAttempt()
        var currentUrl = url
        repeat(MaxRedirects) {
            val connection = opener.open(URI.create(currentUrl))
            try {
                val responseCode = connection.responseCode
                throwIfCancelled()
                if (responseCode in 300..399) {
                    val location = connection.location ?: error("Redirect location missing")
                    currentUrl = URI(currentUrl).resolve(location).toString()
                    return@repeat
                }
                if (responseCode !in 200..299) {
                    error("HTTP $responseCode")
                }

                val totalBytes = connection.contentLength
                connection.inputStream.use { input ->
                    publishAtomically { output ->
                        input.copyToWithProgress(output, totalBytes, throwIfCancelled, onProgress)
                    }
                }
                return
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects")
    }
}

private fun InputStream.copyToWithProgress(
    output: OutputStream,
    totalBytes: Long,
    throwIfCancelled: () -> Unit,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var downloadedBytes = 0L
    onProgress(downloadedBytes, totalBytes)
    while (true) {
        throwIfCancelled()
        val bytesRead = read(buffer)
        if (bytesRead < 0) break
        throwIfCancelled()
        output.write(buffer, 0, bytesRead)
        downloadedBytes += bytesRead
        onProgress(downloadedBytes, totalBytes)
    }
}

private const val MaxRedirects = 5
private const val MaxAttempts = 3
private const val RetryBackoffMillis = 1_000L
