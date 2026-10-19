// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.app.Application
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    application = Application::class,
    shadows = [RealOsRenameShadow::class],
)
class AndroidResourceFileWriteLockOracleTest {

    @Test
    fun concurrentWritesToSameTargetSerializeTheirWriterCallbacks() = withTempDirectory { root ->
        val target = File(root, "same-target.dat")
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondCallStarted = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val events = java.util.concurrent.CopyOnWriteArrayList<String>()
        val executor = writerExecutor()
        try {
            val first = executor.submit {
                writeAtomically(target) { output ->
                    events += "first-enter"
                    firstEntered.countDown()
                    check(releaseFirst.await(3, SECONDS)) { "First writer was not released" }
                    output.write("first".encodeToByteArray())
                    events += "first-exit"
                }
            }
            assertTrue("First writer should enter", firstEntered.await(3, SECONDS))

            val second = executor.submit {
                secondCallStarted.countDown()
                writeAtomically(target) { output ->
                    events += "second-enter"
                    secondEntered.countDown()
                    output.write("second".encodeToByteArray())
                }
            }
            assertTrue("Second write call should start", secondCallStarted.await(3, SECONDS))
            assertFalse(
                "A second callback for the same path must wait for the first write",
                secondEntered.await(300, MILLISECONDS),
            )

            releaseFirst.countDown()
            first.get(3, SECONDS)
            second.get(3, SECONDS)

            assertEquals(listOf("first-enter", "first-exit", "second-enter"), events.toList())
            assertEquals("second", target.readText())
            assertNoTemporaryFiles(target)
        } finally {
            releaseFirst.countDown()
            shutdownExecutor(executor)
        }
    }

    @Test
    fun differentTargetsCanWriteWhileOneTargetIsBlocked() = withTempDirectory { root ->
        val firstTarget = File(root, "first.dat")
        val secondTarget = File(root, "second.dat")
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)
        val executor = writerExecutor()
        try {
            val first = executor.submit {
                writeAtomically(firstTarget) { output ->
                    firstEntered.countDown()
                    check(releaseFirst.await(3, SECONDS)) { "First writer was not released" }
                    output.write("first".encodeToByteArray())
                }
            }
            assertTrue("First writer should enter", firstEntered.await(3, SECONDS))

            val second = executor.submit {
                writeAtomically(secondTarget) { output ->
                    secondEntered.countDown()
                    output.write("second".encodeToByteArray())
                }
            }

            assertTrue("A different target should proceed independently", secondEntered.await(3, SECONDS))
            second.get(3, SECONDS)
            assertFalse("The first target should still be blocked", first.isDone)
            assertEquals("second", secondTarget.readText())

            releaseFirst.countDown()
            first.get(3, SECONDS)
            assertEquals("first", firstTarget.readText())
        } finally {
            releaseFirst.countDown()
            shutdownExecutor(executor)
        }
    }

    @Test
    fun failedWriteReleasesItsTargetLockAndKeepsThePreviousFile() = withTempDirectory { root ->
        val target = File(root, "failure-target.dat").apply { writeText("previous") }
        val executor = writerExecutor()
        try {
            val failed = executor.submit {
                writeAtomically(target) { output ->
                    output.write("partial".encodeToByteArray())
                    throw IOException("expected writer failure")
                }
            }

            val error = try {
                failed.get(3, SECONDS)
                null
            } catch (error: ExecutionException) {
                error
            }
            assertTrue("The writer failure should be rethrown", error?.cause is IOException)
            assertEquals("previous", target.readText())

            val secondEntered = CountDownLatch(1)
            val recovered = executor.submit {
                writeAtomically(target) { output ->
                    secondEntered.countDown()
                    output.write("recovered".encodeToByteArray())
                }
            }
            assertTrue("A later writer should acquire the released target lock", secondEntered.await(3, SECONDS))
            recovered.get(3, SECONDS)

            assertEquals("recovered", target.readText())
            assertNoTemporaryFiles(target)
        } finally {
            shutdownExecutor(executor)
        }
    }

    private inline fun <T> withTempDirectory(block: (File) -> T): T {
        val root = Files.createTempDirectory("skipi-android-resource-write-lock-").toFile()
        return try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writerExecutor() = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "resource-write-lock-oracle").apply { isDaemon = true }
    }

    private fun shutdownExecutor(executor: java.util.concurrent.ExecutorService) {
        executor.shutdownNow()
        assertTrue("Writer threads should stop", executor.awaitTermination(3, SECONDS))
    }

    private fun assertNoTemporaryFiles(target: File) {
        assertFalse(
            target.parentFile.listFiles().orEmpty().any { file ->
                file.name.startsWith("${target.name}.") && file.name.endsWith(".tmp")
            },
        )
    }
}
