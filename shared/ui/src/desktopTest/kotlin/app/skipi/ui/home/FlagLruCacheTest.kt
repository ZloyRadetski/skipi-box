// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlagLruCacheTest {
    @Test
    fun evictsLeastRecentlyUsedBeyondCapacity() = runBlocking {
        val cache = FlagLruCache(capacity = 2)
        cache.put("US", null)
        cache.put("DE", null)
        cache.lookup("US") // US becomes most recent
        cache.put("FR", null)
        assertEquals(2, cache.size())
        assertTrue(cache.lookup("US").first)
        assertFalse(cache.lookup("DE").first)
        assertTrue(cache.lookup("FR").first)
    }

    @Test
    fun remembersMissingFlagAsNull() = runBlocking {
        val cache = FlagLruCache(capacity = 3)
        cache.put("XX", null)
        val (hit, value) = cache.lookup("XX")
        assertTrue(hit)
        assertEquals<ImageBitmap?>(null, value)
    }
}
