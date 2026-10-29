// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import features.logs.AppLog

object AndroidResourceFileLogger {
    private const val Tag = "AndroidResourceFiles"

    fun info(message: String) {
        AppLog.info(Tag, message)
    }

    fun warn(message: String, error: Throwable? = null) {
        AppLog.warn(Tag, message, error)
    }

    fun error(message: String, error: Throwable? = null) {
        AppLog.error(Tag, message, error)
    }
}
