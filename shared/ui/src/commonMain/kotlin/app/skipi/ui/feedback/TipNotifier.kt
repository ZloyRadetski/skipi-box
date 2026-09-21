// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.feedback

import androidx.compose.runtime.compositionLocalOf

fun interface TipNotifier {
    fun show(message: String)
}

val LocalTipNotifier = compositionLocalOf<TipNotifier> {
    TipNotifier { message ->
        println("[TipNotifier] $message")
    }
}
