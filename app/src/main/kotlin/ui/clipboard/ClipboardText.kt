// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.clipboard

import android.content.ClipData
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

const val PlainTextLabel = "plain text"
private const val SensitiveClipExtra = "android.content.extra.IS_SENSITIVE"

suspend fun Clipboard.getPlainText(): String? {
    val clipData = getClipEntry()?.clipData ?: return null
    if (clipData.itemCount <= 0) {
        return null
    }
    return clipData.getItemAt(0)?.text?.toString()
}

suspend fun Clipboard.setPlainText(text: String) {
    val clip = ClipData.newPlainText(PlainTextLabel, text)
    // Server links and proxy credentials pass through here; keep them out of
    // the clipboard preview that other apps and the screen can show.
    clip.description.extras = PersistableBundle().apply {
        putBoolean(SensitiveClipExtra, true)
    }
    setClipEntry(ClipEntry(clip))
}
