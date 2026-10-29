// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReadTextLimitedTest {
    @Test
    fun readsTextUpToTheLimit() {
        assertEquals("привет", "привет".byteInputStream().readTextLimited(maxBytes = 64))
        assertEquals("abcd", "abcd".byteInputStream().readTextLimited(maxBytes = 4))
    }

    @Test
    fun failsInsteadOfBufferingAnOversizedBody() {
        assertFailsWith<IllegalStateException> { "abcde".byteInputStream().readTextLimited(maxBytes = 4) }
    }
}
