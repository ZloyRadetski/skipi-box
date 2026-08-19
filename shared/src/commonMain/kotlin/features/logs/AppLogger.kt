// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

object AppLogger {
    fun debug(tag: String, message: String) {
        println("[$tag] DEBUG: $message")
    }

    fun info(tag: String, message: String) {
        println("[$tag] INFO: $message")
    }

    fun warn(tag: String, message: String, error: Throwable? = null) {
        println("[$tag] WARN: $message ${error?.message.orEmpty()}")
    }

    fun error(tag: String, message: String, error: Throwable? = null) {
        System.err.println("[$tag] ERROR: $message ${error?.message.orEmpty()}")
    }
}
