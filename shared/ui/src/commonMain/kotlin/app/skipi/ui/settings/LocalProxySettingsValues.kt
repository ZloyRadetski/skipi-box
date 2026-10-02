// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

fun sanitizeLocalProxyPort(value: String): String = value.filter(Char::isDigit).take(5)

fun isValidLocalProxyPort(value: String): Boolean = value.toIntOrNull() in 1..65535
