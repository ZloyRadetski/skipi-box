// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.feedback

/**
 * Desktop TipNotifier: surfaces messages on stdout until a richer in-window
 * notification UI is wired into the desktop shell.
 */
class DesktopTipNotifier : TipNotifier {
    override suspend fun show(message: String) {
        println("[SKIPI] $message")
    }

    override suspend fun showError(error: Throwable, fallbackMessage: String?) {
        show(error.tipMessage(fallbackMessage))
    }
}
