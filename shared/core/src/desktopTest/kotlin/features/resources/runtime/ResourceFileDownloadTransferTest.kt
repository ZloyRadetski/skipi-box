// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import features.resources.ResourceFileDownloadCancelledException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceFileDownloadTransferTest {
    @Test
    fun followsRelativeRedirectWithOneAttemptOpenerAndDisconnectsEveryResponse() {
        val target = AtomicMemoryTarget("redirected.dat", "previous")
        val requestedUris = mutableListOf<URI>()
        val first = FakeConnection(
            responseCodeValue = 302,
            location = "../payload",
        )
        val finalBody = "redirected payload".encodeToByteArray()
        val second = FakeConnection(
            responseCodeValue = 200,
            contentLength = finalBody.size.toLong(),
            body = ByteArrayInputStream(finalBody),
        )
        val connections = listOf(first, second)
        var nextConnection = 0
        val attempts = RecordingAttemptFactory {
            FakeOpener { uri ->
                requestedUris += uri
                connections[nextConnection++]
            }
        }

        transfer(attempts, target).download(
            url = "https://resources.example.test/redirect/start",
            onProgress = { _, _ -> },
        )

        assertEquals(
            listOf(
                URI("https://resources.example.test/redirect/start"),
                URI("https://resources.example.test/payload"),
            ),
            requestedUris,
        )
        assertEquals(1, attempts.beginCount)
        assertEquals(1, first.disconnectCount)
        assertEquals(1, second.disconnectCount)
        assertContentEquals(finalBody, target.publishedBytes)
    }

    @Test
    fun followsFourRedirectsButRejectsAFifthRedirectWithoutAnotherRequest() {
        val successful = redirectCase(redirectResponses = 4)
        successful.transfer.download(
            url = "https://resources.example.test/hop/0",
            onProgress = { _, _ -> },
        )
        assertEquals(5, successful.requestedUris.size)
        assertEquals(1, successful.attempts.beginCount)
        assertTrue(successful.connections.all { it.disconnectCount == 1 })
        assertEquals("last allowed hop", successful.target.publishedBytes.decodeToString())

        val rejected = redirectCase(redirectResponses = 5)
        val error = assertFailsWith<IllegalStateException> {
            rejected.transfer.download(
                url = "https://resources.example.test/hop/0",
                onProgress = { _, _ -> },
            )
        }
        assertEquals("Too many redirects", error.message)
        assertEquals(5, rejected.requestedUris.size)
        assertEquals(1, rejected.attempts.beginCount)
        assertTrue(rejected.connections.all { it.disconnectCount == 1 })
        assertEquals("previous", rejected.target.publishedBytes.decodeToString())
    }

    @Test
    fun retriesOnlyIoFailuresThreeTimesWithOneAndTwoSecondBackoffs() {
        val target = AtomicMemoryTarget("geoip.dat", "previous")
        val requestedUris = mutableListOf<URI>()
        val connections = mutableListOf<FakeConnection>()
        val delays = mutableListOf<Long>()
        val attempts = RecordingAttemptFactory {
            val connection = FakeConnection(responseCodeFailure = IOException("connection reset"))
            connections += connection
            FakeOpener { uri ->
                requestedUris += uri
                connection
            }
        }
        val error = assertFailsWith<IOException> {
            transfer(attempts, target, delays = delays).download(
                url = "https://resources.example.test/geoip",
                onProgress = { _, _ -> },
            )
        }

        assertEquals("connection reset", error.message)
        assertEquals(3, attempts.beginCount)
        assertEquals(3, requestedUris.size)
        assertEquals(listOf(1_000L, 2_000L), delays)
        assertTrue(connections.all { it.disconnectCount == 1 })
        assertEquals(0, target.publishCount)
        assertEquals("previous", target.publishedBytes.decodeToString())
    }

    @Test
    fun invalidUriFailsAfterStartingOneAttemptButBeforeOpeningAConnection() {
        val target = AtomicMemoryTarget("invalid.dat", "previous")
        var openCount = 0
        val delays = mutableListOf<Long>()
        val attempts = RecordingAttemptFactory {
            FakeOpener {
                openCount += 1
                error("an invalid URI must not reach the opener")
            }
        }

        assertFailsWith<IllegalArgumentException> {
            transfer(attempts, target, delays = delays).download(
                url = "http://[invalid",
                onProgress = { _, _ -> },
            )
        }

        assertEquals(1, attempts.beginCount)
        assertEquals(0, openCount)
        assertTrue(delays.isEmpty())
        assertEquals(0, target.publishCount)
    }

    @Test
    fun httpAndRedirectErrorsAreNotRetried() {
        val httpTarget = AtomicMemoryTarget("http-error.dat", "previous")
        val httpConnection = FakeConnection(responseCodeValue = 500)
        val httpRequestedUris = mutableListOf<URI>()
        val httpAttempts = RecordingAttemptFactory {
            FakeOpener { uri ->
                httpRequestedUris += uri
                httpConnection
            }
        }
        val httpDelays = mutableListOf<Long>()
        val httpError = assertFailsWith<IllegalStateException> {
            transfer(httpAttempts, httpTarget, delays = httpDelays).download(
                url = "https://resources.example.test/failure",
                onProgress = { _, _ -> },
            )
        }
        assertEquals("HTTP 500", httpError.message)
        assertEquals(1, httpAttempts.beginCount)
        assertEquals(1, httpRequestedUris.size)
        assertTrue(httpDelays.isEmpty())
        assertEquals(1, httpConnection.disconnectCount)
        assertEquals("previous", httpTarget.publishedBytes.decodeToString())

        val redirectTarget = AtomicMemoryTarget("redirect-error.dat", "previous")
        val redirectConnection = FakeConnection(responseCodeValue = 302, location = null)
        val redirectAttempts = RecordingAttemptFactory {
            FakeOpener { redirectConnection }
        }
        val redirectDelays = mutableListOf<Long>()
        val redirectError = assertFailsWith<IllegalStateException> {
            transfer(redirectAttempts, redirectTarget, delays = redirectDelays).download(
                url = "https://resources.example.test/redirect-without-location",
                onProgress = { _, _ -> },
            )
        }
        assertEquals("Redirect location missing", redirectError.message)
        assertEquals(1, redirectAttempts.beginCount)
        assertTrue(redirectDelays.isEmpty())
        assertEquals(1, redirectConnection.disconnectCount)
        assertEquals("previous", redirectTarget.publishedBytes.decodeToString())
    }

    @Test
    fun reportsKnownAndUnknownLengthsAndPublishesCompletedBodies() {
        val knownBody = "known payload".encodeToByteArray()
        val knownTarget = AtomicMemoryTarget("known.dat", "old")
        val knownProgress = mutableListOf<Pair<Long, Long>>()
        val knownConnection = FakeConnection(
            responseCodeValue = 200,
            contentLength = knownBody.size.toLong(),
            body = ByteArrayInputStream(knownBody),
        )
        transfer(
            RecordingAttemptFactory { FakeOpener { knownConnection } },
            knownTarget,
        ).download("https://resources.example.test/known") { downloaded, total ->
            knownProgress += downloaded to total
        }

        assertContentEquals(knownBody, knownTarget.publishedBytes)
        assertEquals(1, knownTarget.publishCount)
        assertEquals(0L to knownBody.size.toLong(), knownProgress.first())
        assertEquals(knownBody.size.toLong() to knownBody.size.toLong(), knownProgress.last())
        assertTrue(knownProgress.all { (_, total) -> total == knownBody.size.toLong() })
        assertEquals(1, knownConnection.disconnectCount)

        val unknownBody = "chunked payload".encodeToByteArray()
        val unknownTarget = AtomicMemoryTarget("unknown.dat", "old")
        val unknownProgress = mutableListOf<Pair<Long, Long>>()
        val unknownConnection = FakeConnection(
            responseCodeValue = 200,
            contentLength = -1L,
            body = ByteArrayInputStream(unknownBody),
        )
        transfer(
            RecordingAttemptFactory { FakeOpener { unknownConnection } },
            unknownTarget,
        ).download("https://resources.example.test/unknown") { downloaded, total ->
            unknownProgress += downloaded to total
        }

        assertContentEquals(unknownBody, unknownTarget.publishedBytes)
        assertEquals(0L to -1L, unknownProgress.first())
        assertEquals(unknownBody.size.toLong() to -1L, unknownProgress.last())
        assertTrue(unknownProgress.all { (_, total) -> total == -1L })
        assertEquals(1, unknownConnection.disconnectCount)
    }

    @Test
    fun emptyBodyAndReadFailuresDoNotReplacePreviouslyPublishedBytes() {
        val emptyTarget = AtomicMemoryTarget("empty.dat", "previous")
        val emptyConnection = FakeConnection(
            responseCodeValue = 200,
            contentLength = 0L,
            body = ByteArrayInputStream(byteArrayOf()),
        )
        val emptyError = assertFailsWith<IllegalStateException> {
            transfer(
                RecordingAttemptFactory { FakeOpener { emptyConnection } },
                emptyTarget,
            ).download("https://resources.example.test/empty") { _, _ -> }
        }
        assertEquals("empty.dat is empty", emptyError.message)
        assertEquals(1, emptyConnection.disconnectCount)
        assertEquals(1, emptyTarget.publishCount)
        assertEquals("previous", emptyTarget.publishedBytes.decodeToString())

        val readTarget = AtomicMemoryTarget("partial.dat", "previous")
        val readConnections = mutableListOf<FakeConnection>()
        val readDelays = mutableListOf<Long>()
        val readAttempts = RecordingAttemptFactory {
            val connection = FakeConnection(
                responseCodeValue = 200,
                contentLength = 20L,
                body = inputThatFailsAfter("partial bytes".encodeToByteArray()),
            )
            readConnections += connection
            FakeOpener { connection }
        }
        assertFailsWith<IOException> {
            transfer(readAttempts, readTarget, delays = readDelays)
                .download("https://resources.example.test/partial") { _, _ -> }
        }
        assertEquals(3, readAttempts.beginCount)
        assertEquals(listOf(1_000L, 2_000L), readDelays)
        assertTrue(readConnections.all { it.disconnectCount == 1 })
        assertEquals(3, readTarget.publishCount)
        assertEquals("previous", readTarget.publishedBytes.decodeToString())
    }

    @Test
    fun cancellationDuringCopyMapsToSharedExceptionAndDoesNotPublish() {
        val target = AtomicMemoryTarget("cancelled.dat", "previous")
        val connection = FakeConnection(
            responseCodeValue = 200,
            contentLength = 5L,
            body = ByteArrayInputStream("fresh".encodeToByteArray()),
        )
        val cancellation = FakeCancellation()
        val delays = mutableListOf<Long>()
        val attempts = RecordingAttemptFactory { FakeOpener { connection } }

        val error = assertFailsWith<ResourceFileDownloadCancelledException> {
            transfer(
                attempts = attempts,
                target = target,
                cancellation = cancellation,
                delays = delays,
            ).download("https://resources.example.test/cancel") { downloaded, _ ->
                if (downloaded > 0L) cancellation.cancel()
            }
        }

        assertEquals("Resource file update cancelled", error.message)
        assertEquals(1, attempts.beginCount)
        assertEquals(1, connection.disconnectCount)
        assertTrue(delays.isEmpty())
        assertEquals(1, target.publishCount)
        assertEquals("previous", target.publishedBytes.decodeToString())
    }

    private fun transfer(
        attempts: ResourceFileDownloadAttemptFactory,
        target: AtomicMemoryTarget,
        cancellation: FakeCancellation = FakeCancellation(),
        delays: MutableList<Long> = mutableListOf(),
    ) = ResourceFileDownloadTransfer(
        attempts = attempts,
        publishAtomically = target::publishAtomically,
        isCancelled = cancellation::isCancelled,
        throwIfCancelled = cancellation::throwIfCancelled,
        waitBeforeRetry = { delay -> delays += delay },
    )

    private fun redirectCase(redirectResponses: Int): RedirectCase {
        val target = AtomicMemoryTarget("redirect-limit.dat", "previous")
        val requestedUris = mutableListOf<URI>()
        val connections = mutableListOf<FakeConnection>()
        val attempts = RecordingAttemptFactory {
            FakeOpener { uri ->
                val hop = requestedUris.size
                requestedUris += uri
                val isRedirect = hop < redirectResponses
                val body = if (isRedirect) byteArrayOf() else "last allowed hop".encodeToByteArray()
                FakeConnection(
                    responseCodeValue = if (isRedirect) 302 else 200,
                    location = (hop + 1).toString().takeIf { isRedirect },
                    contentLength = body.size.toLong(),
                    body = ByteArrayInputStream(body),
                ).also(connections::add)
            }
        }
        return RedirectCase(
            target = target,
            requestedUris = requestedUris,
            connections = connections,
            attempts = attempts,
            transfer = transfer(attempts, target),
        )
    }

    private fun inputThatFailsAfter(bytes: ByteArray): InputStream = object : InputStream() {
        private var offset = 0

        override fun read(): Int {
            if (offset >= bytes.size) throw IOException("body read failed")
            return bytes[offset++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (this.offset >= bytes.size) throw IOException("body read failed")
            val count = minOf(length, bytes.size - this.offset)
            bytes.copyInto(buffer, offset, this.offset, this.offset + count)
            this.offset += count
            return count
        }
    }

    private class RecordingAttemptFactory(
        private val openerForAttempt: (Int) -> ResourceFileDownloadConnectionOpener,
    ) : ResourceFileDownloadAttemptFactory {
        var beginCount: Int = 0
            private set

        override fun beginAttempt(): ResourceFileDownloadConnectionOpener {
            beginCount += 1
            return openerForAttempt(beginCount)
        }
    }

    private class FakeOpener(
        private val openConnection: (URI) -> ResourceFileDownloadConnection,
    ) : ResourceFileDownloadConnectionOpener {
        override fun open(uri: URI): ResourceFileDownloadConnection = openConnection(uri)
    }

    private class FakeConnection(
        private val responseCodeValue: Int = 200,
        private val responseCodeFailure: IOException? = null,
        override val location: String? = null,
        override val contentLength: Long = -1L,
        private val body: InputStream = ByteArrayInputStream(byteArrayOf()),
    ) : ResourceFileDownloadConnection {
        var disconnectCount: Int = 0
            private set

        override val responseCode: Int
            get() {
                responseCodeFailure?.let { throw it }
                return responseCodeValue
            }

        override val inputStream: InputStream
            get() = body

        override fun disconnect() {
            disconnectCount += 1
        }
    }

    private class FakeCancellation {
        private var cancelled = false

        fun cancel() {
            cancelled = true
        }

        fun isCancelled(): Boolean = cancelled

        fun throwIfCancelled() {
            if (cancelled) throw ResourceFileDownloadCancelledException()
        }
    }

    private class AtomicMemoryTarget(
        private val name: String,
        initialContents: String,
    ) {
        var publishedBytes: ByteArray = initialContents.encodeToByteArray()
            private set
        var publishCount: Int = 0
            private set

        fun publishAtomically(write: (OutputStream) -> Unit) {
            publishCount += 1
            val staged = ByteArrayOutputStream()
            write(staged)
            val bytes = staged.toByteArray()
            if (bytes.isEmpty()) error("$name is empty")
            publishedBytes = bytes
        }
    }

    private data class RedirectCase(
        val target: AtomicMemoryTarget,
        val requestedUris: List<URI>,
        val connections: List<FakeConnection>,
        val attempts: RecordingAttemptFactory,
        val transfer: ResourceFileDownloadTransfer,
    )
}
