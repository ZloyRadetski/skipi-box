// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package utils

fun String.shellQuote(): String {
    return "'${replace("'", "'\"'\"'")}'"
}

fun String.shellQuoteForCase(): String {
    return replace("\\", "\\\\")
        .replace("'", "'\"'\"'")
        .replace("*", "\\*")
        .replace("?", "\\?")
        .replace("[", "\\[")
}
