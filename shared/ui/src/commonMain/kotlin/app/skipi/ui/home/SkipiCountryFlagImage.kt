// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.skipi.ui.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import app.skipi.ui.server.display.CountryFlagUtils
import org.jetbrains.compose.resources.decodeToImageBitmap
import utils.runCatchingCancellable

/** Most recently used flags kept decoded; a flag is a few KB, this only bounds the worst case. */
private const val FLAG_CACHE_CAPACITY = 300

/** Size-capped LRU guarded by a mutex; a null value remembers a code that has no bundled flag. */
internal class FlagLruCache(private val capacity: Int) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, ImageBitmap?>()

    /** Non-suspending read for composition; reports a miss while a writer holds the lock. */
    fun peek(code: String): ImageBitmap? {
        if (!mutex.tryLock()) return null
        try {
            return touch(code)
        } finally {
            mutex.unlock()
        }
    }

    suspend fun lookup(code: String): Pair<Boolean, ImageBitmap?> = mutex.withLock {
        val hit = entries.containsKey(code)
        hit to touch(code)
    }

    suspend fun put(code: String, bitmap: ImageBitmap?) = mutex.withLock {
        entries.remove(code)
        entries[code] = bitmap
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    suspend fun size(): Int = mutex.withLock { entries.size }

    private fun touch(code: String): ImageBitmap? {
        if (!entries.containsKey(code)) return null
        val value = entries.remove(code)
        entries[code] = value
        return value
    }
}

private val flagBitmapCache = FlagLruCache(FLAG_CACHE_CAPACITY)

private suspend fun loadFlagBitmap(code: String): ImageBitmap? {
    val (hit, cached) = flagBitmapCache.lookup(code)
    if (hit) return cached
    val bitmap = withContext(Dispatchers.Default) {
        runCatchingCancellable { Res.readBytes("files/flags/$code.png").decodeToImageBitmap() }.getOrNull()
    }
    flagBitmapCache.put(code, bitmap)
    return bitmap
}

/**
 * The bundled flag picture for the leading flag emoji in [flag], or null while it loads
 * and when the text has no flag with a bundled picture, so callers fall back to the emoji or an icon.
 */
@Composable
fun rememberCountryFlagBitmap(flag: String?): ImageBitmap? {
    val code = CountryFlagUtils.countryCodeOfFlag(flag) ?: return null
    val bitmap by produceState(initialValue = flagBitmapCache.peek(code), code) {
        value = loadFlagBitmap(code)
    }
    return bitmap
}

@Composable
fun SkipiCountryFlagImage(bitmap: ImageBitmap, modifier: Modifier = Modifier) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
