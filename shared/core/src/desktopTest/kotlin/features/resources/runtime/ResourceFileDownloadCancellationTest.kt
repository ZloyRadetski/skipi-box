// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import features.resources.ResourceFileDownloadCancelledException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behavior mirror for the shared JVM cancellation object after the Android port. */
class ResourceFileDownloadCancellationTest {
    @Test
    fun beginClearsCancelledFlagAndTrackedReferenceWithoutDisconnectingItAgain() = withCleanState {
        val connection = FakeConnection()
        ResourceFileDownloadCancellation.track(connection)
        ResourceFileDownloadCancellation.cancel()
        assertEquals(1, connection.disconnectCount.get())
        assertTrue(ResourceFileDownloadCancellation.isCancelled())

        ResourceFileDownloadCancellation.begin()

        assertFalse(ResourceFileDownloadCancellation.isCancelled())
        ResourceFileDownloadCancellation.cancel()
        assertEquals(1, connection.disconnectCount.get())
    }

    @Test
    fun cancelDisconnectsTheMostRecentlyTrackedConnection() = withCleanState {
        val first = FakeConnection()
        val latest = FakeConnection()
        ResourceFileDownloadCancellation.track(first)
        ResourceFileDownloadCancellation.track(latest)

        ResourceFileDownloadCancellation.cancel()

        assertEquals(0, first.disconnectCount.get())
        assertEquals(1, latest.disconnectCount.get())
        assertTrue(ResourceFileDownloadCancellation.isCancelled())
    }

    @Test
    fun untrackingAnOlderConnectionDoesNotClearTheNewerTrackedConnection() = withCleanState {
        val older = FakeConnection()
        val newer = FakeConnection()
        ResourceFileDownloadCancellation.track(older)
        ResourceFileDownloadCancellation.track(newer)

        ResourceFileDownloadCancellation.untrack(older)
        ResourceFileDownloadCancellation.cancel()

        assertEquals(0, older.disconnectCount.get())
        assertEquals(1, newer.disconnectCount.get())

        ResourceFileDownloadCancellation.untrack(newer)
        ResourceFileDownloadCancellation.cancel()

        assertEquals(1, newer.disconnectCount.get())
    }

    @Test
    fun trackingAfterCancellationStoresConnectionBeforeThrowing() = withCleanState {
        val connection = FakeConnection()
        ResourceFileDownloadCancellation.cancel()

        val error = captureFailure {
            ResourceFileDownloadCancellation.track(connection)
        }
        assertTrue(error is ResourceFileDownloadCancelledException)
        assertEquals("Resource file update cancelled", error.message)

        ResourceFileDownloadCancellation.cancel()

        assertEquals(1, connection.disconnectCount.get())
    }

    @Test
    fun throwIfCancelledUsesTheExistingDefaultMessage() = withCleanState {
        ResourceFileDownloadCancellation.cancel()

        val error = captureFailure {
            ResourceFileDownloadCancellation.throwIfCancelled()
        }

        assertTrue(error is ResourceFileDownloadCancelledException)
        assertEquals("Resource file update cancelled", error.message)
    }

    @Test
    fun cancelDisconnectsAConnectionWhoseFakeReadIsBlocked() = withCleanState {
        val connection = BlockingReadConnection()
        val readFailure = AtomicReference<Throwable?>(null)
        val readFinished = CountDownLatch(1)
        val reader = Thread {
            try {
                connection.inputStream.read()
            } catch (error: Throwable) {
                readFailure.set(error)
            } finally {
                readFinished.countDown()
            }
        }
        ResourceFileDownloadCancellation.track(connection)
        reader.start()

        try {
            assertTrue(connection.readStarted.await(2, TimeUnit.SECONDS))
            ResourceFileDownloadCancellation.cancel()
            assertTrue(readFinished.await(2, TimeUnit.SECONDS))
            assertEquals("fake stream released by disconnect", readFailure.get()?.message)
            assertEquals(1, connection.disconnectCount.get())
        } finally {
            if (reader.isAlive) {
                ResourceFileDownloadCancellation.cancel()
                reader.interrupt()
                reader.join(1_000)
            }
        }
    }
}

private inline fun withCleanState(block: () -> Unit) {
    ResourceFileDownloadCancellation.begin()
    try {
        block()
    } finally {
        ResourceFileDownloadCancellation.begin()
    }
}

private fun captureFailure(block: () -> Unit): Throwable = try {
    block()
    error("Expected cancellation to throw")
} catch (failure: ResourceFileDownloadCancelledException) {
    failure
}

private open class FakeConnection : HttpURLConnection(URL("http://example.test")) {
    val disconnectCount = AtomicInteger()

    override fun disconnect() {
        disconnectCount.incrementAndGet()
    }

    override fun usingProxy(): Boolean = false

    override fun connect() = Unit
}

private class BlockingReadConnection : FakeConnection() {
    val readStarted = CountDownLatch(1)
    private val disconnected = CountDownLatch(1)

    override fun disconnect() {
        super.disconnect()
        disconnected.countDown()
    }

    override fun getInputStream(): InputStream = object : InputStream() {
        override fun read(): Int {
            readStarted.countDown()
            if (!disconnected.await(3, TimeUnit.SECONDS)) {
                throw IOException("fake blocking read timed out")
            }
            throw IOException("fake stream released by disconnect")
        }
    }
}
