// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.feedback

import android.content.Context
import android.widget.Toast
import app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidToastTipNotifier(context: Context) : TipNotifier {
    private val appContext = context.applicationContext

    override suspend fun show(message: String) {
        withContext(Dispatchers.Main.immediate) {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    override suspend fun showError(error: Throwable, fallbackMessage: String?) {
        show(error.tipMessage(fallbackMessage))
    }
}

private fun Throwable.tipMessage(fallbackMessage: String? = null): String {
    return message.orEmpty().ifBlank {
        fallbackMessage.orEmpty().ifBlank { this::class.simpleName.orEmpty() }
    }
}
