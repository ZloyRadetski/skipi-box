// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.clipboard

import androidx.compose.ui.platform.Clipboard

const val PlainTextLabel = "plain text"

expect suspend fun Clipboard.getPlainText(): String?

expect suspend fun Clipboard.setPlainText(text: String)
