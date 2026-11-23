// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopAtomicFileTest {
    @Test
    fun concurrentSavesOfOneFileNeverFailAndLeaveOneCompleteVersionAndNoTemporaries() {
        val directory = Files.createTempDirectory("skipi-atomic")
        val target = directory.resolve("library.json")
        val start = CountDownLatch(1)
        val failures = java.util.concurrent.atomic.AtomicInteger()
        val threads = (1..8).map { n ->
            thread {
                start.await()
                repeat(50) {
                    runCatching { writeTextAtomically(target, "version-$n-" + "x".repeat(10_000)) }
                        .onFailure { failures.incrementAndGet() }
                }
            }
        }
        start.countDown()
        threads.forEach { it.join() }

        assertEquals(0, failures.get())
        val text = Files.readString(target)
        assertTrue(Regex("version-[0-9]-x{10000}").matches(text), "the file must hold one whole version")
        assertEquals(listOf("library.json"), Files.list(directory).use { it.map { p -> p.fileName.toString() }.toList() })
        directory.toFile().deleteRecursively()
    }
}
