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
import app.skipi.ui.server.display.CountryFlagUtils
import org.jetbrains.compose.resources.decodeToImageBitmap

/** Decoded flag bitmaps by ISO code; a null value remembers a code that has no bundled flag. */
private val flagBitmapCache = HashMap<String, ImageBitmap?>()

private suspend fun loadFlagBitmap(code: String): ImageBitmap? {
    if (flagBitmapCache.containsKey(code)) return flagBitmapCache[code]
    val bitmap = runCatching { Res.readBytes("files/flags/$code.png").decodeToImageBitmap() }.getOrNull()
    flagBitmapCache[code] = bitmap
    return bitmap
}

/**
 * The bundled flag picture for the leading flag emoji in [flag], or null while it loads
 * and when the text has no flag with a bundled picture, so callers fall back to the emoji or an icon.
 */
@Composable
fun rememberCountryFlagBitmap(flag: String?): ImageBitmap? {
    val code = CountryFlagUtils.countryCodeOfFlag(flag) ?: return null
    val bitmap by produceState(initialValue = flagBitmapCache[code], code) {
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
