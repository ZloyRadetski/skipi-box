// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.IOException
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopResourceFilePickerTest {

    @Test
    fun injectedPickerRunsOnSwingEdtAndReturnsTheSelectedPath() = runBlocking {
        val selectedPath = Path.of(System.getProperty("java.io.tmpdir"), "resource-picker-selection.dat")

        val result = chooseDesktopResourceFile { chooser ->
            assertTrue(SwingUtilities.isEventDispatchThread())
            chooser.selectedFile = selectedPath.toFile()
            JFileChooser.APPROVE_OPTION
        }

        assertEquals(selectedPath, result)
    }

    @Test
    fun pickerFailurePropagatesToTheSuspendedCaller() = runBlocking {
        val expected = IOException("picker failed")

        val actual = assertFailsWith<IOException> {
            withTimeout(2_000) {
                chooseDesktopResourceFile {
                    assertTrue(SwingUtilities.isEventDispatchThread())
                    throw expected
                }
            }
        }

        assertEquals(expected.message, actual.message)
    }
}
