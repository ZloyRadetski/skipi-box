// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopClassArchiveTest {
    @Test
    fun theFolderForTheClassArchiveIsCreatedUnderTheLocalAppDataFolder() {
        val localAppData = Files.createTempDirectory("local-app-data")
        try {
            val folder = ensureClassArchiveFolder(localAppData.toString(), isWindows = true)

            assertEquals(localAppData.resolve("SKIPI"), folder)
            assertTrue(folder!!.exists() && folder.isDirectory())
        } finally {
            localAppData.toFile().deleteRecursively()
        }
    }

    @Test
    fun anExistingFolderIsLeftAlone() {
        val localAppData = Files.createTempDirectory("local-app-data")
        try {
            val existing = Files.createDirectories(localAppData.resolve("SKIPI"))
            val marker = Files.createFile(existing.resolve("skipi.jsa"))

            ensureClassArchiveFolder(localAppData.toString(), isWindows = true)

            assertTrue(marker.exists())
        } finally {
            localAppData.toFile().deleteRecursively()
        }
    }

    @Test
    fun nothingIsCreatedOffWindowsOrWithoutAFolderOrWhenItCannotBeCreated() {
        val localAppData = Files.createTempDirectory("local-app-data")
        try {
            assertNull(ensureClassArchiveFolder(localAppData.toString(), isWindows = false))
            assertTrue(!localAppData.resolve("SKIPI").exists())
            assertNull(ensureClassArchiveFolder(null, isWindows = true))
            assertNull(ensureClassArchiveFolder("", isWindows = true))
            // A file where the folder must go: creating it fails, and the start must go on.
            val blocker = Files.createFile(localAppData.resolve("blocked"))
            assertNull(ensureClassArchiveFolder(blocker.toString(), isWindows = true))
        } finally {
            localAppData.toFile().deleteRecursively()
        }
    }

    @Test
    fun aShortLivedInstanceEndsWithoutWritingAClassArchiveWhileANormalExitWritesOne() {
        if (!System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)) return
        val java = ProcessHandle.current().info().command().orElseThrow()
        val folder = Files.createTempDirectory("class-archive-probe")
        try {
            // The archive is only written for classes that come from jars, so the two classes the probe needs go
            // into one; the Kotlin standard library is a jar already.
            val probeJar = folder.resolve("probe.jar")
            JarOutputStream(Files.newOutputStream(probeJar)).use { jar ->
                for (type in listOf(ExitWithoutArchiveProbe::class.java, Class.forName("app.skipi.desktop.DesktopClassArchiveKt"))) {
                    val classes = Path.of(type.protectionDomain.codeSource.location.toURI()).resolve("app/skipi/desktop")
                    Files.list(classes).use { files ->
                        files.filter { file -> file.fileName.toString().let { it.startsWith(type.simpleName) && it.endsWith(".class") } }
                            .forEach { file ->
                                jar.putNextEntry(JarEntry("app/skipi/desktop/" + file.fileName))
                                Files.copy(file, jar)
                                jar.closeEntry()
                            }
                    }
                }
            }
            val classPath = listOf(probeJar, Path.of(Unit::class.java.protectionDomain.codeSource.location.toURI()))
                .joinToString(File.pathSeparator)
            fun run(mode: String): Int {
                val output = folder.resolve("$mode.out")
                val process = ProcessBuilder(
                    java,
                    "-XX:+AutoCreateSharedArchive",
                    "-XX:SharedArchiveFile=" + folder.resolve("$mode.jsa"),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp",
                    classPath,
                    "app.skipi.desktop.ExitWithoutArchiveProbe",
                    mode,
                ).redirectErrorStream(true).redirectOutput(output.toFile()).start()
                assertTrue(process.waitFor(60, TimeUnit.SECONDS), "the probe JVM ended")
                assertEquals(0, process.exitValue(), "probe '$mode' output: " + Files.readString(output))
                return process.exitValue()
            }

            assertEquals(0, run("normal"))
            assertTrue(folder.resolve("normal.jsa").exists(), "a normal exit writes the archive")
            assertEquals(0, run("skip"))
            assertTrue(!folder.resolve("skip.jsa").exists(), "a short-lived instance writes none")
        } finally {
            folder.toFile().deleteRecursively()
        }
    }
}
