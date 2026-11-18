// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreLogMessageLimitTest {
    @Test
    fun anOversizedLineIsShortenedInTheInMemoryEntry() {
        val repository = InMemoryCoreLogRepository()

        repository.append("info", "x".repeat(CoreLogMaxMessageChars * 10))

        val message = repository.entries.value.single().message
        assertTrue(message.length <= CoreLogMaxMessageChars + 1, "length ${message.length}")
        assertTrue(message.startsWith("xxxx"))
        assertTrue(message.endsWith("…"))
    }

    @Test
    fun aLineWithinTheLimitIsKeptAsIs() {
        val repository = InMemoryCoreLogRepository()
        val line = "y".repeat(CoreLogMaxMessageChars)

        repository.append("info", line)

        assertEquals(line, repository.entries.value.single().message)
    }

    @Test
    fun theCommittedBatchHookStillSeesTheFullLine() {
        val seen = mutableListOf<String>()
        val repository = object : InMemoryCoreLogRepository() {
            override fun onAppended(lines: List<ParsedCoreLogLine>) {
                lines.forEach { seen += it.message }
            }
        }
        val line = "z".repeat(CoreLogMaxMessageChars * 2)

        repository.append("info", line)

        assertEquals(listOf(line), seen)
    }
}
