// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.clipboard

import androidx.compose.ui.platform.Clipboard
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual suspend fun Clipboard.getPlainText(): String? {
    return runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
            clipboard.getData(DataFlavor.stringFlavor) as? String
        } else {
            null
        }
    }.getOrNull()
}

actual suspend fun Clipboard.setPlainText(text: String) {
    runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
    }
}
