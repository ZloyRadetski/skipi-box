// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package system

import engine.network.readUtf8Limited
import java.io.InputStream

/** A user-chosen file that is bigger than the app is willing to load into memory. */
internal class FileTooLargeException(val maxBytes: Int) :
    IllegalStateException("The selected file is too large (limit ${maxBytes / (1024 * 1024)} MB)")

/** Reads a user-chosen UTF-8 text file, failing with [FileTooLargeException] instead of exhausting memory. */
internal fun InputStream.readUserFileText(maxBytes: Int): String =
    readUtf8Limited(maxBytes) { FileTooLargeException(maxBytes) }

internal const val MaxServerImportFileBytes = 32 * 1024 * 1024
internal const val MaxBackupFileBytes = 256 * 1024 * 1024
