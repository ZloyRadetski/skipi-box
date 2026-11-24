// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Writes [target] through a temporary sibling file under [withResourceFileWriteLock] and
 * publishes it with an atomic replace, so readers never observe a missing or half-written
 * file.  An empty result is rejected and the temporary file is always removed.
 *
 * [replace] defaults to `Files.move`, which needs Android API 26; the Android client
 * passes `Os.rename` because its minSdk is lower.
 */
fun writeResourceFileAtomically(
    target: File,
    replace: (source: File, target: File) -> Unit = ::moveAtomically,
    write: (OutputStream) -> Unit,
) {
    val parent = target.parentFile ?: error("Parent directory is unavailable for ${target.absolutePath}")
    Files.createDirectories(parent.toPath())
    withResourceFileWriteLock(target) {
        val prefix = "${target.name}.".let { value ->
            if (value.length >= 3) value else value.padEnd(3, '_')
        }
        val temporary = File.createTempFile(prefix, ".tmp", parent)
        try {
            temporary.outputStream().use(write)
            if (temporary.length() <= 0) {
                error("${target.name} is empty")
            }
            replace(temporary, target)
        } finally {
            temporary.delete()
        }
    }
}

private fun moveAtomically(source: File, target: File) {
    Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
}
