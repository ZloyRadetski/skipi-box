// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.skipi.ui.theme.LocalAppColors
import app.skipi.ui.theme.SkipiTheme
import features.config.CustomThemeNameMaxLength
import features.config.CustomThemeShare
import features.config.cleanCustomThemeName
import features.config.indexOfColours
import features.config.isFull
import features.config.parseCustomThemeCodeOrNull
import features.config.readabilityProblems
import features.config.toLink
import features.config.withAdded
import features.config.withRenamed
import features.config.withoutIndex
import org.jetbrains.compose.resources.stringResource
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.custom_themes_active
import app.skipi.ui.resources.custom_themes_add
import app.skipi.ui.resources.custom_themes_add_apply
import app.skipi.ui.resources.custom_themes_already_saved
import app.skipi.ui.resources.custom_themes_copy_link
import app.skipi.ui.resources.custom_themes_delete
import app.skipi.ui.resources.custom_themes_delete_confirm
import app.skipi.ui.resources.custom_themes_empty
import app.skipi.ui.resources.custom_themes_full
import app.skipi.ui.resources.custom_themes_import
import app.skipi.ui.resources.custom_themes_import_hint
import app.skipi.ui.resources.custom_themes_import_invalid
import app.skipi.ui.resources.custom_themes_link_copied
import app.skipi.ui.resources.custom_themes_link_message
import app.skipi.ui.resources.custom_themes_link_title
import app.skipi.ui.resources.custom_themes_low_contrast
import app.skipi.ui.resources.custom_themes_name
import app.skipi.ui.resources.custom_themes_options
import app.skipi.ui.resources.custom_themes_save
import app.skipi.ui.resources.custom_themes_save_current
import app.skipi.ui.resources.custom_themes_title
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed interface ThemeDialog {
    data object Save : ThemeDialog
    data object Import : ThemeDialog
    data class Options(val index: Int) : ThemeDialog
}

/**
 * The user's saved colour themes: tap one to apply it, save what is on screen, import a code or a
 * `skipi://theme` link, and from a theme's options rename it, copy its link or delete it.
 */
@Composable
internal fun CustomThemesSection(
    state: AppearanceSettingsState,
    onSettingsChange: ((AppearanceSettingsState) -> AppearanceSettingsState) -> Unit,
    onCopyText: (text: String, confirmation: String) -> Unit,
) {
    val themes = state.savedThemes()
    var dialog by remember { mutableStateOf<ThemeDialog?>(null) }
    val colors = SkipiTheme.colors

    fun save(next: List<CustomThemeShare>) = onSettingsChange { it.withCustomThemes(next) }

    SmallTitle(text = stringResource(Res.string.custom_themes_title))
    SkipiSettingsSectionCard {
        if (themes.isEmpty()) {
            Text(
                text = stringResource(Res.string.custom_themes_empty),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        themes.forEachIndexed { index, theme ->
            ThemeRow(
                theme = theme,
                active = state.isShowing(theme),
                onApply = { onSettingsChange { it.withCustomTheme(theme) } },
                onOptions = { dialog = ThemeDialog.Options(index) },
            )
        }
        ArrowPreference(
            title = stringResource(Res.string.custom_themes_save_current),
            onClick = { dialog = ThemeDialog.Save },
        )
        ArrowPreference(
            title = stringResource(Res.string.custom_themes_import),
            onClick = { dialog = ThemeDialog.Import },
        )
    }

    when (val shown = dialog) {
        null -> Unit
        ThemeDialog.Save -> {
            val current = colors.toCustomTheme(name = "")
            ThemeDialogFrame(onDismiss = { dialog = null }) {
                var name by remember { mutableStateOf("") }
                DialogTitle(stringResource(Res.string.custom_themes_save_current))
                ThemePreview(current)
                NameField(name) { name = it }
                ThemeNotes(theme = current, library = themes)
                DialogButtons(
                    onCancel = { dialog = null },
                    confirmText = stringResource(Res.string.custom_themes_save),
                    confirmEnabled = !themes.isFull() && themes.indexOfColours(current) < 0,
                    onConfirm = {
                        save(themes.withAdded(current.copy(name = name)))
                        dialog = null
                    },
                )
            }
        }

        ThemeDialog.Import -> ThemeDialogFrame(onDismiss = { dialog = null }) {
            var text by remember { mutableStateOf("") }
            val parsed = remember(text) { parseCustomThemeCodeOrNull(text) }
            DialogTitle(stringResource(Res.string.custom_themes_import))
            TextField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(Res.string.custom_themes_import_hint),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (parsed != null) {
                ThemePreview(parsed)
                ThemeNotes(theme = parsed, library = themes)
            } else if (text.isNotBlank()) {
                Text(
                    text = stringResource(Res.string.custom_themes_import_invalid),
                    style = MiuixTheme.textStyles.body2,
                    color = LocalAppColors.current.error,
                )
            }
            val canAdd = parsed != null && !themes.isFull() && themes.indexOfColours(parsed) < 0
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(text = stringResource(Res.string.common_cancel), onClick = { dialog = null })
                Spacer(Modifier.width(8.dp))
                TextButton(
                    text = stringResource(Res.string.custom_themes_add),
                    enabled = canAdd,
                    onClick = {
                        if (parsed != null) save(themes.withAdded(parsed))
                        dialog = null
                    },
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = parsed != null,
                    onClick = {
                        if (parsed != null) {
                            // Already saved colours are applied without a second copy.
                            val next = themes.withAdded(parsed)
                            onSettingsChange { it.withCustomThemes(next).withCustomTheme(parsed) }
                        }
                        dialog = null
                    },
                    colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary),
                ) {
                    Text(
                        text = stringResource(Res.string.custom_themes_add_apply),
                        color = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        is ThemeDialog.Options -> {
            val theme = themes.getOrNull(shown.index)
            if (theme == null) {
                dialog = null
            } else {
                ThemeDialogFrame(onDismiss = { dialog = null }) {
                    var name by remember(shown.index) { mutableStateOf(theme.name) }
                    var confirmDelete by remember(shown.index) { mutableStateOf(false) }
                    val linkCopied = stringResource(Res.string.custom_themes_link_copied)
                    DialogTitle(stringResource(Res.string.custom_themes_options))
                    ThemePreview(theme)
                    NameField(name) { name = it }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        text = stringResource(Res.string.custom_themes_copy_link),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onCopyText(theme.toLink(), linkCopied) },
                    )
                    TextButton(
                        text = stringResource(
                            if (confirmDelete) Res.string.custom_themes_delete_confirm else Res.string.custom_themes_delete,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (confirmDelete) {
                                save(themes.withoutIndex(shown.index))
                                dialog = null
                            } else {
                                confirmDelete = true
                            }
                        },
                    )
                    DialogButtons(
                        onCancel = { dialog = null },
                        confirmText = stringResource(Res.string.custom_themes_save),
                        confirmEnabled = name.cleanCustomThemeName().isNotEmpty(),
                        onConfirm = {
                            save(themes.withRenamed(shown.index, name))
                            dialog = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(
    theme: CustomThemeShare,
    active: Boolean,
    onApply: () -> Unit,
    onOptions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onApply)
            .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatches(theme)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.name,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurface,
            )
            if (active) {
                Text(
                    text = stringResource(Res.string.custom_themes_active),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        TextButton(text = "⋯", onClick = onOptions)
    }
}

/** Background, surface, accent and text of a theme as four small squares. */
@Composable
private fun Swatches(theme: CustomThemeShare) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, MiuixTheme.colorScheme.dividerLine, shape),
    ) {
        listOf(theme.background, theme.surface, theme.accent, theme.text).forEach { color ->
            Box(Modifier.size(width = 14.dp, height = 32.dp).background(Color(color)))
        }
    }
}

@Composable
private fun ThemePreview(theme: CustomThemeShare) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(shape)
            .background(Color(theme.background))
            .border(1.dp, MiuixTheme.colorScheme.dividerLine, shape)
            .padding(12.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(theme.surface))
                    .padding(10.dp),
            ) {
                Column {
                    Text(text = theme.name.ifEmpty { "…" }, color = Color(theme.text))
                    Text(text = "SKIPI", color = Color(theme.textSecondary), style = MiuixTheme.textStyles.body2)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(theme.accent))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(text = "●", color = Color(theme.text))
            }
        }
    }
}

@Composable
private fun NameField(name: String, onChange: (String) -> Unit) {
    TextField(
        value = name,
        onValueChange = { onChange(it.take(CustomThemeNameMaxLength)) },
        label = stringResource(Res.string.custom_themes_name),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Why the theme can't be added, or that it is hard to read; nothing when it is fine. */
@Composable
private fun ThemeNotes(theme: CustomThemeShare, library: List<CustomThemeShare>) {
    val notes = buildList {
        if (library.isFull()) add(stringResource(Res.string.custom_themes_full))
        else if (library.indexOfColours(theme) >= 0) add(stringResource(Res.string.custom_themes_already_saved))
        if (theme.readabilityProblems().isNotEmpty()) add(stringResource(Res.string.custom_themes_low_contrast))
    }
    notes.forEach { note ->
        Text(
            text = note,
            modifier = Modifier.padding(top = 4.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        style = MiuixTheme.textStyles.title3,
        color = MiuixTheme.colorScheme.onSurface,
    )
}

@Composable
private fun DialogButtons(
    onCancel: () -> Unit,
    confirmText: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(text = stringResource(Res.string.common_cancel), onClick = onCancel)
        Spacer(Modifier.width(8.dp))
        Button(
            enabled = confirmEnabled,
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.primary),
        ) {
            Text(text = confirmText, color = MiuixTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun ThemeDialogFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                colors = CardDefaults.defaultColors(
                    color = LocalAppColors.current.surface,
                    contentColor = LocalAppColors.current.onSurface,
                ),
            ) {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    content()
                }
            }
        }
    }
}

/**
 * Asks before a theme that arrived through a `skipi://theme` link is added: any web page can send one, so
 * nothing is saved until the user has seen the colours and agreed.
 */
@Composable
fun CustomThemeLinkDialog(
    theme: CustomThemeShare,
    library: List<CustomThemeShare>,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    ThemeDialogFrame(onDismiss = onDismiss) {
        DialogTitle(stringResource(Res.string.custom_themes_link_title))
        Text(
            text = stringResource(Res.string.custom_themes_link_message),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        ThemePreview(theme)
        ThemeNotes(theme = theme, library = library)
        DialogButtons(
            onCancel = onDismiss,
            confirmText = stringResource(Res.string.custom_themes_add),
            confirmEnabled = !library.isFull() && library.indexOfColours(theme) < 0,
            onConfirm = onAdd,
        )
    }
}
