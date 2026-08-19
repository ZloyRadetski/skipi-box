// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.feedback

interface TipNotifier {
    suspend fun show(message: String)
    suspend fun showError(error: Throwable, fallbackMessage: String? = null)
}

fun Throwable.tipMessage(fallbackMessage: String? = null): String {
    return message.orEmpty().ifBlank {
        fallbackMessage.orEmpty().ifBlank { this::class.simpleName.orEmpty() }
    }
}
