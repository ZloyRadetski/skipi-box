// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfCodeEditorTest {

    @Test
    fun testConfCodeEditorState() {
        val state = ConfCodeEditorState("initial raw config")
        assertEquals("initial raw config", state.snapshotText())
        state.replaceText("updated raw config")
        assertEquals("updated raw config", state.snapshotText())
    }
}
