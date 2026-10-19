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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Android-source oracle for the global resource download cancellation state. */
class AndroidResourceFileDownloadCancellationOracleTest {
    @Before
    fun resetCancellationState() {
        AndroidResourceFileDownloadCancellation.begin()
    }

    @After
    fun clearCancellationState() {
        AndroidResourceFileDownloadCancellation.begin()
    }

    @Test
    fun beginClearsCancelledFlagAndTrackedReferenceWithoutDisconnectingItAgain() {
        val connection = FakeConnection()
        AndroidResourceFileDownloadCancellation.track(connection)
        AndroidResourceFileDownloadCancellation.cancel()
        assertEquals(1, connection.disconnectCount.get())
        assertTrue(AndroidResourceFileDownloadCancellation.isCancelled())

        AndroidResourceFileDownloadCancellation.begin()

        assertFalse(AndroidResourceFileDownloadCancellation.isCancelled())
        AndroidResourceFileDownloadCancellation.cancel()
        assertEquals(1, connection.disconnectCount.get())
    }

    @Test
    fun cancelDisconnectsTheMostRecentlyTrackedConnection() {
        val first = FakeConnection()
        val latest = FakeConnection()
        AndroidResourceFileDownloadCancellation.track(first)
        AndroidResourceFileDownloadCancellation.track(latest)

        AndroidResourceFileDownloadCancellation.cancel()

        assertEquals(0, first.disconnectCount.get())
        assertEquals(1, latest.disconnectCount.get())
        assertTrue(AndroidResourceFileDownloadCancellation.isCancelled())
    }

    @Test
    fun untrackingAnOlderConnectionDoesNotClearTheNewerTrackedConnection() {
        val older = FakeConnection()
        val newer = FakeConnection()
        AndroidResourceFileDownloadCancellation.track(older)
        AndroidResourceFileDownloadCancellation.track(newer)

        AndroidResourceFileDownloadCancellation.untrack(older)
        AndroidResourceFileDownloadCancellation.cancel()

        assertEquals(0, older.disconnectCount.get())
        assertEquals(1, newer.disconnectCount.get())

        AndroidResourceFileDownloadCancellation.untrack(newer)
        AndroidResourceFileDownloadCancellation.cancel()

        assertEquals(1, newer.disconnectCount.get())
    }

    @Test
    fun trackingAfterCancellationStoresConnectionBeforeThrowing() {
        val connection = FakeConnection()
        AndroidResourceFileDownloadCancellation.cancel()

        val error = captureFailure {
            AndroidResourceFileDownloadCancellation.track(connection)
        }
        assertTrue("expected shared cancellation exception, got $error", error is ResourceFileDownloadCancelledException)
        assertEquals("Resource file update cancelled", error?.message)

        AndroidResourceFileDownloadCancellation.cancel()

        assertEquals(1, connection.disconnectCount.get())
    }

    @Test
    fun throwIfCancelledUsesTheExistingDefaultMessage() {
        AndroidResourceFileDownloadCancellation.cancel()

        val error = captureFailure {
            AndroidResourceFileDownloadCancellation.throwIfCancelled()
        }

        assertTrue("expected shared cancellation exception, got $error", error is ResourceFileDownloadCancelledException)
        assertEquals("Resource file update cancelled", error?.message)
    }

    @Test
    fun cancelDisconnectsAConnectionWhoseFakeReadIsBlocked() {
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
        AndroidResourceFileDownloadCancellation.track(connection)
        reader.start()

        try {
            assertTrue(connection.readStarted.await(2, TimeUnit.SECONDS))
            AndroidResourceFileDownloadCancellation.cancel()
            assertTrue(readFinished.await(2, TimeUnit.SECONDS))
            assertEquals("fake stream released by disconnect", readFailure.get()?.message)
            assertEquals(1, connection.disconnectCount.get())
        } finally {
            if (reader.isAlive) {
                AndroidResourceFileDownloadCancellation.cancel()
                reader.interrupt()
                reader.join(1_000)
            }
        }
    }
}

private fun captureFailure(block: () -> Unit): Throwable? = try {
    block()
    null
} catch (error: Throwable) {
    error
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
