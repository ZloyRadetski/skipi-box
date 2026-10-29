// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/*
 * Rows for the host-specific parts of settings pages (`platformContent`). They use the same Miuix controls and section
 * card as the shared pages, so a page reads as one surface and follows the active theme.
 */

@Composable
fun SkipiSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    SmallTitle(text = title)
    SkipiSettingsSectionCard {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
fun SkipiSettingsActionRow(title: String, summary: String, onClick: () -> Unit) {
    ArrowPreference(title = title, summary = summary.ifBlank { null }, onClick = onClick)
}

@Composable
fun SkipiSettingsInfoRow(title: String, summary: String) {
    BasicComponent(title = title, summary = summary.ifBlank { null })
}

@Composable
fun SkipiSettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SwitchPreference(title = title, summary = summary, checked = checked, onCheckedChange = onCheckedChange)
}

@Composable
fun SkipiSettingsNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { SkipiSettingsInlineError(it) }
    }
}

@Composable
fun SkipiSettingsTextField(value: String, onValueChange: (String) -> Unit, label: String, readOnly: Boolean = false) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            readOnly = readOnly,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SkipiSettingsInlineError(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MiuixTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp),
    )
}

@Composable
fun SkipiSettingsStatusMessage(message: String) {
    if (message.isNotBlank()) {
        Text(
            text = message,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
