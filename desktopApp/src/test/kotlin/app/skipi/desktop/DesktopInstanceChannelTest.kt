// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopInstanceChannelTest {
    private val directory = createTempDirectory("skipi-inst")
    private val socket = directory.resolve("instance.sock")
    private val opened = mutableListOf<DesktopInstanceChannel>()

    @AfterTest
    fun closeAll() {
        opened.forEach { it.close() }
        directory.toFile().deleteRecursively()
    }

    private fun claim(messages: List<String>) =
        DesktopInstanceChannel.claimOrForward(socket, messages)?.also(opened::add)

    @Test
    fun aSecondLaunchHandsItsLinksToTheRunningInstanceAndDoesNotStart() {
        val received = LinkedBlockingQueue<String>()
        val primary = assertNotNull(claim(emptyList()))
        primary.listen(received::put)

        val second = claim(listOf("skipi://toggle", "skipi://open"))

        assertNull(second)
        assertEquals("skipi://toggle", received.poll(5, TimeUnit.SECONDS))
        assertEquals("skipi://open", received.poll(5, TimeUnit.SECONDS))
    }

    @Test
    fun aLeftoverSocketFileOfACrashedInstanceIsReplaced() {
        Files.writeString(socket, "stale")

        assertNotNull(claim(emptyList()))
    }

    @Test
    fun closingTheInstanceRemovesItsSocketSoTheNextLaunchStarts() {
        val primary = assertNotNull(claim(emptyList()))
        primary.close()

        assertFalse(Files.exists(socket))
        assertNotNull(claim(emptyList()))
    }

    @Test
    fun messagesFromSeveralLaunchesAllArrive() {
        val received = LinkedBlockingQueue<String>()
        assertNotNull(claim(emptyList())).listen(received::put)

        repeat(3) { assertNull(claim(listOf("skipi://toggle"))) }

        repeat(3) { assertEquals("skipi://toggle", received.poll(5, TimeUnit.SECONDS)) }
    }
}
