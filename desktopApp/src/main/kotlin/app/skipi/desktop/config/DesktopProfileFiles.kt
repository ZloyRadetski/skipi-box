// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Explicit user-driven file access for portable SKIPI/Shadowrocket profiles. */
object DesktopProfileFiles {
    private val profileFilter = FileNameExtensionFilter("SKIPI / Shadowrocket profile (*.conf)", "conf")

    fun chooseProfileToOpen(): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Open SKIPI profile"
            fileFilter = profileFilter
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return null
        return chooser.selectedFile?.toPath()
    }

    fun chooseProfileToSave(): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Save SKIPI profile"
            fileFilter = profileFilter
            isAcceptAllFileFilterUsed = false
            selectedFile = java.io.File("skipi-profile.conf")
        }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
        val selected = chooser.selectedFile.toPath()
        return selected.takeIf { it.fileName.toString().endsWith(".conf", ignoreCase = true) }
            ?: selected.resolveSibling("${selected.fileName}.conf")
    }

    fun read(path: Path): Result<String> = runCatching {
        Files.readString(path, StandardCharsets.UTF_8)
    }

    fun write(path: Path, content: String): Result<Unit> = try {
        Files.writeString(path, content, StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING)
        Result.success(Unit)
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
