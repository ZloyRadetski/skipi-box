// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every icon of the app goes through SkipiIcon, so the "Icon color" setting reaches all of them. This fails the
 * moment a screen draws an icon with the library's Icon directly (a new icon, or one that was missed).
 */
class IconColorGuardTest {
    private val root: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val sourceFiles: List<File> = listOf("shared", "app", "desktopApp")
        .map { File(root, it) }
        .filter { it.isDirectory }
        .flatMap { dir ->
            dir.walkTopDown()
                .onEnter { it.name != "build" && it.name != ".gradle" }
                .filter { it.isFile && it.extension == "kt" }
                .filter { "/src/" in it.invariantSeparatorsPath }
                .filter { Regex("/src/[A-Za-z]*[Tt]est[A-Za-z]*/").containsMatchIn(it.invariantSeparatorsPath).not() }
                .filter { it.name != "SkipiIcon.kt" }
                .toList()
        }

    @Test
    fun noScreenImportsTheLibrarysIconDirectly() {
        assertTrue(sourceFiles.size > 200, "the guard must see the sources (saw ${sourceFiles.size})")
        val offenders = sourceFiles.filter { file ->
            file.readLines().any {
                it.trim() == "import top.yukonga.miuix.kmp.basic.Icon" || it.trim() == "import androidx.compose.material3.Icon"
            }
        }.map { it.relativeTo(root).invariantSeparatorsPath }

        assertTrue(offenders.isEmpty(), "Use SkipiIcon / SkipiContentIcon instead of Icon in:\n" + offenders.joinToString("\n"))
    }
}
