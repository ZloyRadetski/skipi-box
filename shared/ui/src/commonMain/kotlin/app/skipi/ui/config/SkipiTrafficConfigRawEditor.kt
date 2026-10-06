// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.skipi.ui.theme.SkipiTheme
import features.config.ShadowrocketConfigDiagnostic
import features.config.ShadowrocketConfigDiagnosticSeverity
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SkipiTrafficConfigRawEditorState(
    val configId: Int?,
    val name: String,
    val sourceUrl: String,
    val updateLocked: Boolean,
    val content: String,
    val ruleCount: Int,
    val proxyGroupCount: Int,
    val diagnostics: List<ShadowrocketConfigDiagnostic>,
) {
    val hasErrors: Boolean
        get() = diagnostics.any { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }

    val canSave: Boolean
        get() = name.isNotBlank() && content.isNotBlank() &&
            !hasErrors
}

data class SkipiTrafficConfigRawEditorLabels(
    val newConfigTitle: String,
    val editConfigTitle: String,
    val subtitle: String,
    val save: String,
    val name: String,
    val sourceUrl: String,
    val lockUpdates: String,
    val lockUpdatesSummary: String,
    val parsedTitle: String,
    val invalidTitle: String,
    val rules: String,
    val proxyGroups: String,
    val diagnostics: String,
    val content: String,
)

/** Shared profile editor form. The host owns drafts, validation, and persistence. */
@Composable
fun SkipiTrafficConfigRawEditor(
    state: SkipiTrafficConfigRawEditorState,
    labels: SkipiTrafficConfigRawEditorLabels,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onNameChange: (String) -> Unit,
    onSourceUrlChange: (String) -> Unit,
    onUpdateLockedChange: (Boolean) -> Unit,
    onContentChange: (String) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    key(state.configId) {
        SkipiTrafficConfigFullScreenScaffold(
            title = if (state.configId == null) labels.newConfigTitle else labels.editConfigTitle,
            padding = padding,
            isWideScreen = isWideScreen,
            saveLabel = labels.save,
            onBack = onBack,
            actions = {
                TextButton(text = labels.save, enabled = state.canSave, onClick = onSave)
            },
        ) { _, listPadding, _ ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(listPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(labels.subtitle, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            state = rememberTextFieldState(initialText = state.name),
                            inputTransformation = { onNameChange(asCharSequence().toString()) },
                            label = labels.name,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextField(
                            state = rememberTextFieldState(initialText = state.sourceUrl),
                            inputTransformation = { onSourceUrlChange(asCharSequence().toString()) },
                            label = labels.sourceUrl,
                            lineLimits = TextFieldLineLimits.SingleLine,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        SwitchPreference(
                            title = labels.lockUpdates,
                            summary = labels.lockUpdatesSummary,
                            checked = state.updateLocked,
                            onCheckedChange = onUpdateLockedChange,
                        )
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (state.hasErrors) labels.invalidTitle else labels.parsedTitle,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.hasErrors) MiuixTheme.colorScheme.error else SkipiTheme.colors.accent,
                        )
                        Text("${labels.rules}: ${state.ruleCount} · ${labels.proxyGroups}: ${state.proxyGroupCount} · ${labels.diagnostics}: ${state.diagnostics.size}", style = MiuixTheme.textStyles.body2)
                        state.diagnostics.take(4).forEach { diagnostic ->
                            val color = if (diagnostic.severity == ShadowrocketConfigDiagnosticSeverity.Error) {
                                MiuixTheme.colorScheme.error
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            }
                            Text("${diagnostic.severity}: ${diagnostic.message}", color = color, style = MiuixTheme.textStyles.body2)
                        }
                    }
                }
                TextField(
                    state = rememberTextFieldState(initialText = state.content),
                    inputTransformation = { onContentChange(asCharSequence().toString()) },
                    label = labels.content,
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 20, maxHeightInLines = 1000),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
