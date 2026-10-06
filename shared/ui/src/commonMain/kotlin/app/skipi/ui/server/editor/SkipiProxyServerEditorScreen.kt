// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.components.IconDropdownMenu
import app.skipi.ui.components.IconDropdownMenuEntry
import app.skipi.ui.components.NavigationIcon
import app.skipi.ui.components.WarningConfirmDialog
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageContentPaddingWithIme
import app.skipi.ui.layout.pageListPadding
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_copy
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_more
import app.skipi.ui.resources.common_save
import app.skipi.ui.theme.SkipiTheme
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class ProxyServerEditorDialogText(
    val fullValidationTitle: String,
    val fullValidationSummary: String,
    val returnEdit: String,
    val continueSave: String,
    val discardTitle: String,
    val discardSummary: String,
    val discard: String,
)

/** Shared full-screen editor presentation; hosts own navigation, clipboard, and draft restoration. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiProxyServerEditorScreen(
    padding: PaddingValues,
    serverEdit: ProxyServer<*>,
    title: String,
    isWideScreen: Boolean,
    options: ProxyServerEditorOptions,
    hasChanges: () -> Boolean,
    messages: ProxyServerEditorDialogText,
    fullValidationMessages: List<String>,
    showDiscardConfirmation: Boolean,
    showFullValidationWarning: Boolean,
    onRequestBack: () -> Unit,
    onRequestDiscardConfirmation: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onCopy: (() -> Unit)?,
    onDelete: (() -> Unit)? = null,
    onSave: () -> Unit,
    onDismissFullValidation: () -> Unit,
    onContinueFullValidation: () -> Unit,
    customEditor: @Composable (Custom, PaddingValues) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = SkipiTheme.colors.background,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackNavigationIcon(onClick = {
                        if (hasChanges()) onRequestDiscardConfirmation() else onRequestBack()
                    })
                },
                actions = {
                    if (serverEdit is StrategyGroup) {
                        val menuEntries = buildList {
                            if (onCopy != null) {
                                add(
                                    IconDropdownMenuEntry(
                                        key = "copy",
                                        title = stringResource(Res.string.common_copy),
                                        action = "copy",
                                    ),
                                )
                            }
                            if (onDelete != null) {
                                add(
                                    IconDropdownMenuEntry(
                                        key = "delete",
                                        title = stringResource(Res.string.common_delete),
                                        action = "delete",
                                    ),
                                )
                            }
                        }
                        if (menuEntries.isNotEmpty()) {
                            IconDropdownMenu(
                                imageVector = MiuixIcons.More,
                                contentDescription = stringResource(Res.string.common_more),
                                entries = menuEntries,
                                onAction = { action ->
                                    when (action) {
                                        "copy" -> onCopy?.invoke()
                                        "delete" -> onDelete?.invoke()
                                    }
                                },
                                hapticFeedback = LocalHapticFeedback.current,
                            )
                        }
                    } else if (onCopy != null) {
                        NavigationIcon(onClick = onCopy, imageVector = MiuixIcons.Copy)
                    }
                    NavigationIcon(onClick = onSave, imageVector = MiuixIcons.Ok, contentDescription = stringResource(Res.string.common_save))
                },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)
        val basePadding = pageListPadding(contentPadding)
        val direction = LocalLayoutDirection.current
        val listPadding = PaddingValues(
            start = basePadding.calculateStartPadding(direction) + 12.dp,
            top = basePadding.calculateTopPadding() + 8.dp,
            end = basePadding.calculateEndPadding(direction) + 12.dp,
            bottom = basePadding.calculateBottomPadding() + 12.dp,
        )
        val editorPadding = pageContentPaddingWithIme(listPadding)
        if (serverEdit is Custom) {
            customEditor(serverEdit, contentPadding)
        } else {
            Box(Modifier.fillMaxSize().background(SkipiTheme.colors.background)) {
                LazyColumn(state = listState, modifier = Modifier.pageScrollModifiers(scrollBehavior), contentPadding = editorPadding) {
                    proxyServerEditorContent(serverEdit, options)
                }
                VerticalScrollBar(
                    adapter = rememberScrollBarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    trackPadding = editorPadding,
                )
            }
        }
    }

    val warningColor = MiuixTheme.colorScheme.error
    WarningConfirmDialog(
        show = showFullValidationWarning,
        title = messages.fullValidationTitle,
        summary = messages.fullValidationSummary,
        dismissText = messages.returnEdit,
        confirmText = messages.continueSave,
        onDismissRequest = onDismissFullValidation,
        onConfirm = onContinueFullValidation,
    ) {
        fullValidationMessages.distinct().forEachIndexed { index, message ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(warningColor))
                Spacer(Modifier.width(10.dp))
                Text(message, modifier = Modifier.weight(1f), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onBackground)
            }
        }
    }
    WarningConfirmDialog(
        show = showDiscardConfirmation,
        title = messages.discardTitle,
        summary = messages.discardSummary,
        dismissText = messages.returnEdit,
        confirmText = messages.discard,
        onDismissRequest = onDismissDiscard,
        onConfirm = onConfirmDiscard,
    )
}
