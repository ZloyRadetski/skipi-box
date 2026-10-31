// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

internal fun isEscapeBack(key: Key, type: KeyEventType): Boolean = type == KeyEventType.KeyDown && key == Key.Escape

/**
 * Lets the window's key handler send Escape to whichever page can go back. A page registers while
 * it is on screen and clears the registration when it leaves; with none registered Escape is not
 * consumed.
 */
internal class DesktopEscapeHandler {
    @Volatile
    private var back: (() -> Unit)? = null

    fun register(onBack: (() -> Unit)?) {
        back = onBack
    }

    /** Returns whether the key was used. */
    fun onEscape(): Boolean {
        val handler = back ?: return false
        handler()
        return true
    }
}
