// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import java.io.File

/** Runs [block] under the process-wide write lock for [target]'s absolute path. */
fun <T> withResourceFileWriteLock(target: File, block: () -> T): T {
    val lock = synchronized(ResourceFileWriteLocks) {
        ResourceFileWriteLocks.getOrPut(target.absolutePath) { Any() }
    }
    return synchronized(lock, block)
}

private val ResourceFileWriteLocks = mutableMapOf<String, Any>()
