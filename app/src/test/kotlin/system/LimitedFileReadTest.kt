// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LimitedFileReadTest {
    @Test
    fun readsFileWithinLimit() {
        assertEquals("abcd", "abcd".byteInputStream().readUserFileText(maxBytes = 4))
    }

    @Test
    fun rejectsFileOverLimitWithClearError() {
        try {
            ByteArray(100).inputStream().readUserFileText(maxBytes = 10)
            fail("expected FileTooLargeException")
        } catch (error: FileTooLargeException) {
            assertTrue(error.message.orEmpty().contains("too large"))
        }
    }
}
