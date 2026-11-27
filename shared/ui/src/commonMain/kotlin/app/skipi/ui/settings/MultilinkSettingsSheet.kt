// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.AppWindowBottomSheet
import app.skipi.ui.layout.fadeUnderTopBar
import app.skipi.ui.layout.withTopBarGap
import app.skipi.ui.resources.*
import app.skipi.ui.text.formatTemplate
import features.multilink.MultilinkDraftError
import features.multilink.MultilinkDraftException
import features.multilink.MultilinkLink
import features.multilink.MultilinkLinkDraft
import features.multilink.MultilinkLinkKind
import features.multilink.MultilinkSettings
import features.multilink.MultilinkStatus
import features.multilink.formatMbps
import features.multilink.parseProbeTargets
import features.multilink.probeTargetsText
import features.multilink.withDraft
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun MultilinkLinkSheet(
    link: MultilinkLink?,
    settings: MultilinkSettings,
    onDismiss: () -> Unit,
    onSave: (MultilinkSettings) -> Unit,
) {
    val id = link?.id
    var draft by remember(id) { mutableStateOf(id?.let { MultilinkLinkDraft.of(settings, it) } ?: MultilinkLinkDraft()) }
    var used by remember(id) { mutableStateOf(id != null && id !in settings.excluded) }
    var error by remember(id) { mutableStateOf<MultilinkDraftError?>(null) }
    val save = {
        if (id != null) {
            settings.withDraft(id, draft)
                .onSuccess { onSave(it.withLinkUsed(id, used)) }
                .onFailure { failure -> error = (failure as? MultilinkDraftException)?.error }
        }
    }
    AppWindowBottomSheet(
        show = link != null,
        title = link?.let { settings.displayName(it.id, it.name) }.orEmpty(),
        startAction = { TextButton(text = stringResource(Res.string.common_cancel), onClick = onDismiss) },
        endAction = { TextButton(text = stringResource(Res.string.common_save), onClick = { save() }) },
        onDismissRequest = onDismiss,
    ) {
        key(id) {
            SettingsSheetContent {
                SwitchPreference(
                    title = stringResource(Res.string.multilink_link_use),
                    checked = used,
                    onCheckedChange = { used = it },
                )
                SheetTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = stringResource(Res.string.multilink_link_name),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                SettingsSheetStatusText(stringResource(Res.string.multilink_link_name_help).formatTemplate("name" to link?.name.orEmpty()))
                SheetTextField(
                    value = draft.limitMbps,
                    onValueChange = { draft = draft.copy(limitMbps = it); error = null },
                    label = stringResource(Res.string.multilink_link_limit_field),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                SettingsSheetStatusText(stringResource(Res.string.multilink_link_limit_help))
                if (error == MultilinkDraftError.Limit) {
                    SettingsSheetStatusText(stringResource(Res.string.multilink_link_limit_error), error = true)
                }
                SheetTextField(
                    value = draft.weight,
                    onValueChange = { draft = draft.copy(weight = it); error = null },
                    label = stringResource(Res.string.multilink_link_weight),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                SettingsSheetStatusText(stringResource(Res.string.multilink_link_weight_help))
                if (error == MultilinkDraftError.Weight) {
                    SettingsSheetStatusText(stringResource(Res.string.multilink_link_weight_error), error = true)
                }
                SwitchPreference(
                    title = stringResource(Res.string.multilink_link_phone),
                    summary = stringResource(Res.string.multilink_link_phone_summary),
                    checked = draft.phone,
                    onCheckedChange = { draft = draft.copy(phone = it) },
                )
            }
        }
    }
}

@Composable
internal fun MultilinkTextSheet(
    show: Boolean,
    title: String,
    initial: String,
    help: String,
    error: String,
    parse: (String) -> MultilinkSettings?,
    onDismiss: () -> Unit,
    onSave: (MultilinkSettings) -> Unit,
) {
    var text by remember(show) { mutableStateOf(initial) }
    var invalid by remember(show) { mutableStateOf(false) }
    AppWindowBottomSheet(
        show = show,
        title = title,
        startAction = { TextButton(text = stringResource(Res.string.common_cancel), onClick = onDismiss) },
        endAction = {
            TextButton(
                text = stringResource(Res.string.common_save),
                onClick = { parse(text)?.let(onSave) ?: run { invalid = true } },
            )
        },
        onDismissRequest = onDismiss,
    ) {
        key(show) {
            SettingsSheetContent {
                SheetTextField(
                    value = text,
                    onValueChange = { text = it; invalid = false },
                    label = title,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                SettingsSheetStatusText(help)
                if (invalid) SettingsSheetStatusText(error, error = true)
            }
        }
    }
}
