// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package utils

/** This text as a single POSIX shell word: single-quoted, each inner quote closed, escaped and reopened. */
fun String.shellQuote(): String = "'${replace("'", "'\\''")}'"

/** This text as a PowerShell string literal: single-quoted, with inner quotes doubled. */
fun String.powerShellLiteral(): String = "'${replace("'", "''")}'"
