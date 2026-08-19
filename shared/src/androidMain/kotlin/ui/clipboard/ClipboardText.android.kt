// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.clipboard

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard

actual suspend fun Clipboard.getPlainText(): String? {
    val clipData = getClipEntry()?.clipData ?: return null
    if (clipData.itemCount <= 0) {
        return null
    }
    return clipData.getItemAt(0)?.text?.toString()
}

actual suspend fun Clipboard.setPlainText(text: String) {
    setClipEntry(ClipEntry(ClipData.newPlainText(PlainTextLabel, text)))
}
