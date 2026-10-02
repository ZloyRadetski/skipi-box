// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.then
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_auto_update
import app.skipi.ui.resources.configs_auto_update_interval
import app.skipi.ui.resources.configs_auto_update_summary
import app.skipi.ui.resources.configs_geo_auto_update
import app.skipi.ui.resources.configs_geo_auto_update_interval
import app.skipi.ui.resources.configs_geo_auto_update_summary
import app.skipi.ui.resources.configs_lock_update
import app.skipi.ui.resources.configs_lock_update_summary
import app.skipi.ui.resources.configs_name
import app.skipi.ui.resources.configs_name_summary
import app.skipi.ui.resources.configs_source_url
import app.skipi.ui.resources.configs_source_url_summary
import app.skipi.ui.theme.SkipiTheme
import features.subscription.sanitizeSubscriptionIntervalInput
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** Common profile basics form; persistence and navigation are supplied by the host platform. */
@Composable
fun SkipiTrafficConfigProfileBasics(
    name: String,
    onNameChange: (String) -> Unit,
    sourceUrl: String,
    onSourceUrlChange: (String) -> Unit,
    updateLocked: Boolean,
    onUpdateLockedChange: (Boolean) -> Unit,
    autoUpdate: Boolean,
    onAutoUpdateChange: (Boolean) -> Unit,
    updateInterval: String,
    onUpdateIntervalChange: (String) -> Unit,
    resourceAutoUpdate: Boolean,
    onResourceAutoUpdateChange: (Boolean) -> Unit,
    resourceUpdateInterval: String,
    onResourceUpdateIntervalChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ProfileTextField(name, onNameChange, stringResource(Res.string.configs_name), stringResource(Res.string.configs_name_summary))
        ProfileTextField(sourceUrl, onSourceUrlChange, stringResource(Res.string.configs_source_url), stringResource(Res.string.configs_source_url_summary))
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
        ) {
            SwitchPreference(
                title = stringResource(Res.string.configs_lock_update),
                summary = stringResource(Res.string.configs_lock_update_summary),
                checked = updateLocked,
                onCheckedChange = onUpdateLockedChange,
            )
        }
        if (!updateLocked) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = stringResource(Res.string.configs_auto_update),
                        summary = stringResource(Res.string.configs_auto_update_summary),
                        checked = autoUpdate,
                        onCheckedChange = onAutoUpdateChange,
                    )
                    if (autoUpdate) {
                        IntervalField(updateInterval, onUpdateIntervalChange, stringResource(Res.string.configs_auto_update_interval))
                    }
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            colors = CardDefaults.defaultColors(color = SkipiTheme.colors.surface),
        ) {
            Column(Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = stringResource(Res.string.configs_geo_auto_update),
                    summary = stringResource(Res.string.configs_geo_auto_update_summary),
                    checked = resourceAutoUpdate,
                    onCheckedChange = onResourceAutoUpdateChange,
                )
                if (resourceAutoUpdate) {
                    IntervalField(resourceUpdateInterval, onResourceUpdateIntervalChange, stringResource(Res.string.configs_geo_auto_update_interval))
                }
            }
        }
    }
}

@Composable
private fun ProfileTextField(value: String, onValueChange: (String) -> Unit, label: String, summary: String) {
    Column(Modifier.fillMaxWidth()) {
        TextField(
            state = rememberTextFieldState(value),
            inputTransformation = { onValueChange(asCharSequence().toString()) },
            label = label,
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = summary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun IntervalField(value: String, onValueChange: (String) -> Unit, label: String) {
    TextField(
        state = rememberTextFieldState(initialText = value),
        inputTransformation = InputTransformation
            .byValue { _, proposed -> sanitizeSubscriptionIntervalInput(proposed.toString()) }
            .then { onValueChange(asCharSequence().toString()) },
        label = label,
        lineLimits = TextFieldLineLimits.SingleLine,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    )
}
