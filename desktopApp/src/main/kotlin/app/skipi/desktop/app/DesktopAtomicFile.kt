// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.concurrent.ConcurrentHashMap

private val writeLocks = ConcurrentHashMap<String, Any>()

/**
 * Replaces [path] with [text] in one step. The text goes to a uniquely named sibling first, so two saves of the same
 * file never share (and delete) one temporary file, and saves of one path are serialised; a failed write leaves
 * the previous file untouched and no temporary behind.
 */
internal fun writeTextAtomically(path: Path, text: String) {
    val target = path.toAbsolutePath().normalize()
    target.parent?.let(Files::createDirectories)
    val lock = writeLocks.computeIfAbsent(target.toString()) { Any() }
    synchronized(lock) {
        val temporary = Files.createTempFile(target.parent, "${target.fileName}.", ".tmp")
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8)
            try {
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
