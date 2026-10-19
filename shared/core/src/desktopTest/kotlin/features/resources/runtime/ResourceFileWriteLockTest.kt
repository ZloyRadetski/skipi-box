// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceFileWriteLockTest {

    @Test
    fun callbacksForTheSameAbsolutePathAreSerialized() = withTempDirectory { root ->
        val target = File(root, "same-target.dat")
        val sameAbsolutePath = File(target.absolutePath)
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondCallStarted = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val events = java.util.concurrent.CopyOnWriteArrayList<String>()
        val executor = writerExecutor()
        try {
            val first = executor.submit<String> {
                withResourceFileWriteLock(target) {
                    events += "first-enter"
                    firstEntered.countDown()
                    check(releaseFirst.await(3, SECONDS)) { "First writer was not released" }
                    events += "first-exit"
                    "first"
                }
            }
            assertTrue(firstEntered.await(3, SECONDS), "First callback should enter")

            val second = executor.submit<String> {
                secondCallStarted.countDown()
                withResourceFileWriteLock(sameAbsolutePath) {
                    events += "second-enter"
                    secondEntered.countDown()
                    "second"
                }
            }
            assertTrue(secondCallStarted.await(3, SECONDS), "Second lock call should start")
            assertFalse(
                secondEntered.await(300, MILLISECONDS),
                "A second callback for the same absolute path must wait",
            )

            releaseFirst.countDown()
            assertEquals("first", first.get(3, SECONDS))
            assertEquals("second", second.get(3, SECONDS))
            assertEquals(listOf("first-enter", "first-exit", "second-enter"), events.toList())
        } finally {
            releaseFirst.countDown()
            shutdownExecutor(executor)
        }
    }

    @Test
    fun differentAbsolutePathsCanProceedIndependently() = withTempDirectory { root ->
        val firstTarget = File(root, "first.dat")
        val secondTarget = File(root, "second.dat")
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val executor = writerExecutor()
        try {
            val first = executor.submit<String> {
                withResourceFileWriteLock(firstTarget) {
                    firstEntered.countDown()
                    check(releaseFirst.await(3, SECONDS)) { "First writer was not released" }
                    "first"
                }
            }
            assertTrue(firstEntered.await(3, SECONDS), "First callback should enter")

            val second = executor.submit<String> {
                withResourceFileWriteLock(secondTarget) {
                    secondEntered.countDown()
                    "second"
                }
            }
            assertTrue(secondEntered.await(3, SECONDS), "A different target should proceed independently")
            assertEquals("second", second.get(3, SECONDS))
            assertFalse(first.isDone, "The first target should still be blocked")

            releaseFirst.countDown()
            assertEquals("first", first.get(3, SECONDS))
        } finally {
            releaseFirst.countDown()
            shutdownExecutor(executor)
        }
    }

    @Test
    fun throwingFromTheCallbackReleasesTheTargetLock() = withTempDirectory { root ->
        val target = File(root, "failure-target.dat")
        val executor = writerExecutor()
        try {
            val failed = executor.submit<String> {
                withResourceFileWriteLock(target) {
                    throw IOException("expected writer failure")
                }
            }
            val error = try {
                failed.get(3, SECONDS)
                null
            } catch (error: ExecutionException) {
                error
            }
            assertTrue(error?.cause is IOException, "The callback failure should be rethrown")

            val secondEntered = CountDownLatch(1)
            val recovered = executor.submit<String> {
                withResourceFileWriteLock(File(target.absolutePath)) {
                    secondEntered.countDown()
                    "recovered"
                }
            }
            assertTrue(secondEntered.await(3, SECONDS), "A later callback should acquire the released lock")
            assertEquals("recovered", recovered.get(3, SECONDS))
        } finally {
            shutdownExecutor(executor)
        }
    }

    private inline fun <T> withTempDirectory(block: (File) -> T): T {
        val root = Files.createTempDirectory("skipi-resource-write-lock-").toFile()
        return try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writerExecutor() = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "resource-write-lock-test").apply { isDaemon = true }
    }

    private fun shutdownExecutor(executor: java.util.concurrent.ExecutorService) {
        executor.shutdownNow()
        assertTrue(executor.awaitTermination(3, SECONDS), "Writer threads should stop")
    }
}
