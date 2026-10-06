// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import features.config.ShadowrocketConfigDiagnostic
import features.config.ShadowrocketConfigDiagnosticSeverity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkipiTrafficConfigRawEditorStateTest {
    @Test
    fun saveRequiresNameAndContentAndNoErrorsButAllowsWarnings() {
        assertFalse(state(name = "", content = "valid").canSave)
        assertFalse(state(name = "Profile", content = "").canSave)
        assertFalse(state(name = "Profile", content = "invalid", diagnostics = listOf(error())).canSave)
        assertTrue(state(name = "Profile", content = "valid", diagnostics = listOf(warning())).canSave)
    }

    @Test
    fun parsedPresentationDependsOnDiagnosticsNotNameOrContent() {
        val state = state(name = "", content = "")

        assertFalse(state.canSave)
        assertFalse(state.hasErrors)
    }

    private fun state(
        name: String,
        content: String,
        diagnostics: List<ShadowrocketConfigDiagnostic> = emptyList(),
    ) = SkipiTrafficConfigRawEditorState(
        configId = null,
        name = name,
        sourceUrl = "",
        updateLocked = false,
        content = content,
        ruleCount = 0,
        proxyGroupCount = 0,
        diagnostics = diagnostics,
    )

    private fun error() = ShadowrocketConfigDiagnostic(
        lineNumber = 1,
        message = "invalid config",
        severity = ShadowrocketConfigDiagnosticSeverity.Error,
    )

    private fun warning() = ShadowrocketConfigDiagnostic(
        lineNumber = 1,
        message = "unsupported option",
        severity = ShadowrocketConfigDiagnosticSeverity.Warning,
    )
}
