// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.settings

/** A random `skipi_xxxxxx` login or password for the local proxy's authentication. */
fun generateRandomProxyCredential(prefix: String = "skipi_"): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val randomPart = (1..6).map { chars.random() }.joinToString("")
    return "$prefix$randomPart"
}
