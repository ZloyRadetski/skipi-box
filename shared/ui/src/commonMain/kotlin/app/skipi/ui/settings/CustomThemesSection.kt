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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import app.skipi.ui.resources.custom_themes_new_theme
import app.skipi.ui.resources.custom_themes_options
import app.skipi.ui.resources.custom_themes_save
import app.skipi.ui.resources.custom_themes_save_current
import app.skipi.ui.resources.custom_themes_save_title
import app.skipi.ui.resources.custom_themes_title
import app.skipi.ui.theme.LocalAppColors
import app.skipi.ui.theme.SkipiTheme
import features.config.CustomThemeNameMaxLength
import features.config.CustomThemeShare
import features.config.cleanCustomThemeName
import features.config.contrastRatio
import features.config.indexOfColours
import features.config.isFull
import features.config.parseCustomThemeCodeOrNull
import features.config.readabilityProblems
import features.config.toLink
import features.config.withAdded
import features.config.withRenamed
import features.config.withoutIndex
import org.jetbrains.compose.resources.stringResource
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
            var name by remember { mutableStateOf("") }
            ThemeDialogFrame(onDismiss = { dialog = null }) {
                SaveThemeContent(
                    current = current,
                    name = name,
                    onNameChange = { name = it },
                    library = themes,
                    onCancel = { dialog = null },
                    onSave = {
                        save(themes.withAdded(current.copy(name = name)))
                        dialog = null
                    },
                )
            }
        }

        ThemeDialog.Import -> {
            var text by remember { mutableStateOf("") }
            ThemeDialogFrame(onDismiss = { dialog = null }) {
                ImportThemeContent(
                    text = text,
                    onTextChange = { text = it },
                    library = themes,
                    onCancel = { dialog = null },
                    onAdd = { theme ->
                        save(themes.withAdded(theme))
                        dialog = null
                    },
                    onAddAndApply = { theme ->
                        // Colours that are already saved are applied without a second copy.
                        val next = themes.withAdded(theme)
                        onSettingsChange { it.withCustomThemes(next).withCustomTheme(theme) }
                        dialog = null
                    },
                )
            }
        }

        is ThemeDialog.Options -> {
            val theme = themes.getOrNull(shown.index)
            if (theme == null) {
                dialog = null
            } else {
                var name by remember(shown.index) { mutableStateOf(theme.name) }
                val linkCopied = stringResource(Res.string.custom_themes_link_copied)
                ThemeDialogFrame(onDismiss = { dialog = null }) {
                    ThemeOptionsContent(
                        theme = theme,
                        name = name,
                        onNameChange = { name = it },
                        onCopyLink = { onCopyText(theme.toLink(), linkCopied) },
                        onDelete = {
                            save(themes.withoutIndex(shown.index))
                            dialog = null
                        },
                        onCancel = { dialog = null },
                        onSave = {
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
internal fun SaveThemeContent(
    current: CustomThemeShare,
    name: String,
    onNameChange: (String) -> Unit,
    library: List<CustomThemeShare>,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    DialogTitle(stringResource(Res.string.custom_themes_save_title))
    ThemePreview(current.copy(name = name))
    Spacer(Modifier.height(12.dp))
    NameField(name, onNameChange)
    ThemeNotes(theme = current, library = library)
    Spacer(Modifier.height(20.dp))
    ButtonPair(
        cancelText = stringResource(Res.string.common_cancel),
        onCancel = onCancel,
        confirmText = stringResource(Res.string.custom_themes_save),
        confirmEnabled = !library.isFull() && library.indexOfColours(current) < 0,
        onConfirm = onSave,
    )
}

@Composable
internal fun ImportThemeContent(
    text: String,
    onTextChange: (String) -> Unit,
    library: List<CustomThemeShare>,
    onCancel: () -> Unit,
    onAdd: (CustomThemeShare) -> Unit,
    onAddAndApply: (CustomThemeShare) -> Unit,
) {
    val parsed = remember(text) { parseCustomThemeCodeOrNull(text) }
    DialogTitle(stringResource(Res.string.custom_themes_import))
    TextField(
        value = text,
        onValueChange = onTextChange,
        label = stringResource(Res.string.custom_themes_import_hint),
        modifier = Modifier.fillMaxWidth(),
        singleLine = false,
        maxLines = 4,
    )
    if (parsed != null) {
        Spacer(Modifier.height(12.dp))
        ThemePreview(parsed)
        ThemeNotes(theme = parsed, library = library)
    } else if (text.isNotBlank()) {
        Text(
            text = stringResource(Res.string.custom_themes_import_invalid),
            modifier = Modifier.padding(top = 8.dp),
            style = MiuixTheme.textStyles.body2,
            color = LocalAppColors.current.error,
        )
    }
    Spacer(Modifier.height(20.dp))
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = parsed != null,
        colors = ButtonDefaults.buttonColorsPrimary(),
        onClick = { if (parsed != null) onAddAndApply(parsed) },
    ) {
        Text(
            text = stringResource(Res.string.custom_themes_add_apply),
            color = readableOn(MiuixTheme.colorScheme.primary),
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(
            text = stringResource(Res.string.common_cancel),
            modifier = Modifier.weight(1f),
            onClick = onCancel,
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            text = stringResource(Res.string.custom_themes_add),
            modifier = Modifier.weight(1f),
            enabled = parsed != null && !library.isFull() && library.indexOfColours(parsed) < 0,
            onClick = { if (parsed != null) onAdd(parsed) },
        )
    }
}

@Composable
internal fun ThemeOptionsContent(
    theme: CustomThemeShare,
    name: String,
    onNameChange: (String) -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    DialogTitle(stringResource(Res.string.custom_themes_options))
    ThemePreview(theme.copy(name = name))
    Spacer(Modifier.height(12.dp))
    NameField(name, onNameChange)
    Spacer(Modifier.height(8.dp))
    TextButton(
        text = stringResource(Res.string.custom_themes_copy_link),
        modifier = Modifier.fillMaxWidth(),
        onClick = onCopyLink,
    )
    Spacer(Modifier.height(8.dp))
    TextButton(
        text = stringResource(
            if (confirmDelete) Res.string.custom_themes_delete_confirm else Res.string.custom_themes_delete,
        ),
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
    )
    Spacer(Modifier.height(20.dp))
    ButtonPair(
        cancelText = stringResource(Res.string.common_cancel),
        onCancel = onCancel,
        confirmText = stringResource(Res.string.custom_themes_save),
        confirmEnabled = name.cleanCustomThemeName().isNotEmpty(),
        onConfirm = onSave,
    )
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
            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Swatches(theme)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = theme.name,
                style = MiuixTheme.textStyles.title4,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (active) {
                Text(
                    text = stringResource(Res.string.custom_themes_active),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onOptions),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "⋯",
                fontSize = 22.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

/** Background, surface, accent and text of a theme as four small bars. */
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

/** A small mock of the app in the theme's own colours: a card with the power button and a server line. */
@Composable
private fun ThemePreview(theme: CustomThemeShare) {
    val outer = RoundedCornerShape(16.dp)
    val inner = RoundedCornerShape(12.dp)
    val title = theme.name.ifBlank { stringResource(Res.string.custom_themes_new_theme) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(outer)
            .background(Color(theme.background))
            .border(1.dp, Color(theme.textSecondary).copy(alpha = 0.35f), outer)
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(inner)
                    .background(Color(theme.surface))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(theme.accent)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(16.dp).border(2.dp, Color(theme.text), CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color(theme.text),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "vless · 42 ms",
                        color = Color(theme.textSecondary),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(inner)
                        .background(Color(theme.surfaceVariant))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "SKIPI", color = Color(theme.textSecondary), fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(inner)
                        .background(Color(theme.accent))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "SKIPI", color = Color(theme.text), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
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
            modifier = Modifier.padding(top = 8.dp),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        style = MiuixTheme.textStyles.title3,
        color = MiuixTheme.colorScheme.onSurface,
    )
}

/** Cancel on the left, the main action on the right; both share the width so neither text is squeezed. */
@Composable
private fun ButtonPair(
    cancelText: String,
    onCancel: () -> Unit,
    confirmText: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(text = cancelText, modifier = Modifier.weight(1f), onClick = onCancel)
        Spacer(Modifier.width(8.dp))
        Button(
            modifier = Modifier.weight(1.2f),
            enabled = confirmEnabled,
            colors = ButtonDefaults.buttonColorsPrimary(),
            onClick = onConfirm,
        ) {
            Text(text = confirmText, color = readableOn(MiuixTheme.colorScheme.primary))
        }
    }
}

/** Black or white, whichever reads better on [background]; the host's own on-accent colour can be dark on dark. */
private fun readableOn(background: Color): Color {
    val light = contrastRatio(0xFFFFFFFF, background.toArgb().toLong() and 0xFFFFFFFFL)
    val dark = contrastRatio(0xFF000000, background.toArgb().toLong() and 0xFFFFFFFFL)
    return if (light >= dark) Color.White else Color.Black
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
        Spacer(Modifier.height(12.dp))
        ThemePreview(theme)
        ThemeNotes(theme = theme, library = library)
        Spacer(Modifier.height(20.dp))
        ButtonPair(
            cancelText = stringResource(Res.string.common_cancel),
            onCancel = onDismiss,
            confirmText = stringResource(Res.string.custom_themes_add),
            confirmEnabled = !library.isFull() && library.indexOfColours(theme) < 0,
            onConfirm = onAdd,
        )
    }
}
