// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorResult
import app.skipi.ui.components.IconDropdownMenu
import app.skipi.ui.components.IconDropdownMenuEntry
import app.skipi.ui.components.ImportModeDialog
import app.skipi.ui.layout.pageListPadding
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_clipboard_empty
import app.skipi.ui.resources.common_clipboard_unsupported_format
import app.skipi.ui.resources.common_copied
import app.skipi.ui.resources.common_export_to_clipboard
import app.skipi.ui.resources.common_import_from_clipboard
import app.skipi.ui.resources.common_merge_import
import app.skipi.ui.resources.common_replace_existing
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.proxy_app_list_import_clipboard_message
import app.skipi.ui.resources.proxy_app_list_import_clipboard_title
import app.skipi.ui.resources.proxy_app_list_import_invalid_entry
import app.skipi.ui.resources.proxy_app_list_import_invalid_user
import app.skipi.ui.resources.proxy_app_list_import_no_valid_apps
import app.skipi.ui.resources.proxy_app_list_import_unsupported_mode
import app.skipi.ui.resources.proxy_app_list_imported
import app.skipi.ui.resources.proxy_app_list_mode
import app.skipi.ui.resources.proxy_app_list_mode_blacklist
import app.skipi.ui.resources.proxy_app_list_mode_global
import app.skipi.ui.resources.proxy_app_list_mode_whitelist
import app.skipi.ui.resources.proxy_app_list_more_actions
import app.skipi.ui.resources.proxy_app_list_show_system_apps
import app.skipi.ui.resources.proxy_app_list_title
import app.skipi.ui.text.formatTemplate
import features.clipboard.ClipboardImportException
import features.clipboard.ClipboardImportFailure
import features.config.TrafficConfigState
import features.proxy.app.usecase.ProxyAppListClipboardData
import features.proxy.app.usecase.decodeProxyAppListFromClipboard
import features.proxy.app.usecase.encodeProxyAppListForClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Tune

private enum class ProxyAppListMoreAction {
    ToggleSystemApps,
    ImportClipboard,
    ExportClipboard,
}

/** Shared mode, clipboard, and import-confirmation behavior for one profile's app list. */
@Composable
fun SkipiTrafficConfigProxyAppListEditorScreen(
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
    actionScope: CoroutineScope,
    padding: PaddingValues,
    isWideScreen: Boolean,
    readClipboardText: suspend () -> String?,
    writeClipboardText: suspend (String) -> Unit,
    currentUserId: Int?,
    selfPackageName: String,
    onNotice: suspend (String) -> Unit,
    onBack: (() -> Unit)? = null,
    showSystemApps: Boolean = false,
    onToggleSystemApps: (() -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    appContent: @Composable (
        contentPadding: PaddingValues,
        listPadding: PaddingValues,
        scrollBehavior: ScrollBehavior,
        selectedKeys: Set<String>,
        modeIndex: Int,
        onSelectionChange: (List<String>, Boolean) -> Unit,
    ) -> Unit,
) {
    val modes = listOf(
        stringResource(Res.string.proxy_app_list_mode_blacklist),
        stringResource(Res.string.proxy_app_list_mode_whitelist),
        stringResource(Res.string.proxy_app_list_mode_global),
    )
    val modeIndex = profile.proxyAppListMode.coerceIn(modes.indices)
    val selectedKeys = remember(profile.proxyAppListSelectedApps) {
        profile.proxyAppListSelectedApps.toSet()
    }
    val hapticFeedback = LocalHapticFeedback.current
    val copiedMessage = stringResource(Res.string.common_copied)
    val clipboardEmptyMessage = stringResource(Res.string.common_clipboard_empty)
    val unsupportedClipboardMessage = stringResource(Res.string.common_clipboard_unsupported_format)
    val importTitle = stringResource(Res.string.proxy_app_list_import_clipboard_title)
    val importMessageTemplate = stringResource(Res.string.proxy_app_list_import_clipboard_message)
    val importedMessageTemplate = stringResource(Res.string.proxy_app_list_imported)
    val noValidAppsMessage = stringResource(Res.string.proxy_app_list_import_no_valid_apps)
    val invalidEntryMessage = stringResource(Res.string.proxy_app_list_import_invalid_entry)
    val invalidUserIdMessage = stringResource(Res.string.proxy_app_list_import_invalid_user)
    val unsupportedModeMessage = stringResource(Res.string.proxy_app_list_import_unsupported_mode)
    var pendingImport by remember { mutableStateOf<ProxyAppListClipboardData?>(null) }

    SkipiTrafficConfigFullScreenScaffold(
        title = stringResource(Res.string.proxy_app_list_title),
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = stringResource(Res.string.common_save),
        onBack = onBack,
        actions = {
            ProxyAppListModeMenu(
                modes = modes,
                selectedIndex = modeIndex,
                hapticFeedback = hapticFeedback,
                onSelectedIndexChange = { index ->
                    controller.setProxyAppListMode(profile.id, index)
                },
            )
            ProxyAppListMoreActionsMenu(
                showSystemApps = showSystemApps,
                showSystemAppsAction = onToggleSystemApps != null,
                hapticFeedback = hapticFeedback,
                onAction = { action ->
                    when (action) {
                        ProxyAppListMoreAction.ToggleSystemApps -> onToggleSystemApps?.invoke()
                        ProxyAppListMoreAction.ImportClipboard -> actionScope.launch {
                            val decoded = runCatching {
                                decodeProxyAppListFromClipboard(
                                    text = readClipboardText().orEmpty(),
                                    currentUserId = currentUserId ?: 0,
                                    selfPackageName = selfPackageName,
                                )
                            }
                            val error = decoded.exceptionOrNull()
                            if (error == null) {
                                pendingImport = decoded.getOrThrow()
                            } else {
                                onNotice(
                                    error.proxyAppListClipboardImportMessage(
                                        emptyClipboard = clipboardEmptyMessage,
                                        unsupportedFormat = unsupportedClipboardMessage,
                                        noValidApps = noValidAppsMessage,
                                        invalidEntry = invalidEntryMessage,
                                        invalidUserId = invalidUserIdMessage,
                                        unsupportedMode = unsupportedModeMessage,
                                    ),
                                )
                            }
                        }

                        ProxyAppListMoreAction.ExportClipboard -> actionScope.launch {
                            writeClipboardText(
                                encodeProxyAppListForClipboard(
                                    selectedApps = profile.proxyAppListSelectedApps,
                                    mode = profile.proxyAppListMode,
                                ),
                            )
                            onNotice(copiedMessage)
                        }
                    }
                },
            )
        },
        subtitle = modes[modeIndex],
        topBarBottomContent = headerContent,
    ) { contentPadding, _, scrollBehavior ->
        appContent(
            contentPadding,
            pageListPadding(contentPadding),
            scrollBehavior,
            selectedKeys,
            modeIndex,
        ) { selectionKeys, isChecked ->
            controller.setProxyAppListSelection(profile.id, selectionKeys, isChecked)
        }
    }

    val imported = pendingImport
    ImportModeDialog(
        show = imported != null,
        title = importTitle,
        message = importMessageTemplate.formatTemplate("count" to imported?.selectedApps.orEmpty().size),
        mergeText = stringResource(Res.string.common_merge_import),
        replaceText = stringResource(Res.string.common_replace_existing),
        onDismissRequest = { pendingImport = null },
        onModeSelected = { importMode ->
            val importData = pendingImport ?: return@ImportModeDialog
            val result = controller.applyProxyAppListImport(profile.id, importData, importMode)
            val importedCount = when (result.updateResult) {
                TrafficConfigEditorResult.Saved -> result.importedCount
                TrafficConfigEditorResult.Missing -> 0
                else -> return@ImportModeDialog
            }
            pendingImport = null
            actionScope.launch {
                onNotice(importedMessageTemplate.formatTemplate("count" to importedCount))
            }
        },
    )
}

@Composable
private fun ProxyAppListModeMenu(
    modes: List<String>,
    selectedIndex: Int,
    hapticFeedback: HapticFeedback,
    onSelectedIndexChange: (Int) -> Unit,
) {
    IconDropdownMenu(
        imageVector = MiuixIcons.Tune,
        contentDescription = stringResource(Res.string.proxy_app_list_mode),
        entries = modes.mapIndexed { index, mode ->
            IconDropdownMenuEntry(
                key = mode,
                title = mode,
                selected = selectedIndex == index,
                action = index,
            )
        },
        onAction = onSelectedIndexChange,
        hapticFeedback = hapticFeedback,
    )
}

@Composable
private fun ProxyAppListMoreActionsMenu(
    showSystemApps: Boolean,
    showSystemAppsAction: Boolean,
    hapticFeedback: HapticFeedback,
    onAction: (ProxyAppListMoreAction) -> Unit,
) {
    val entries = buildList {
        if (showSystemAppsAction) {
            add(
                IconDropdownMenuEntry(
                    key = "show-system-apps",
                    title = stringResource(Res.string.proxy_app_list_show_system_apps),
                    selected = showSystemApps,
                    action = ProxyAppListMoreAction.ToggleSystemApps,
                ),
            )
        }
        add(
            IconDropdownMenuEntry(
                key = "import-clipboard",
                title = stringResource(Res.string.common_import_from_clipboard),
                action = ProxyAppListMoreAction.ImportClipboard,
            ),
        )
        add(
            IconDropdownMenuEntry(
                key = "export-clipboard",
                title = stringResource(Res.string.common_export_to_clipboard),
                action = ProxyAppListMoreAction.ExportClipboard,
            ),
        )
    }
    IconDropdownMenu(
        imageVector = MiuixIcons.More,
        contentDescription = stringResource(Res.string.proxy_app_list_more_actions),
        entries = entries,
        onAction = onAction,
        hapticFeedback = hapticFeedback,
    )
}

private fun Throwable.proxyAppListClipboardImportMessage(
    emptyClipboard: String,
    unsupportedFormat: String,
    noValidApps: String,
    invalidEntry: String,
    invalidUserId: String,
    unsupportedMode: String,
): String {
    return when ((this as? ClipboardImportException)?.failure) {
        ClipboardImportFailure.EmptyClipboard -> emptyClipboard
        ClipboardImportFailure.NoValidApps -> noValidApps
        ClipboardImportFailure.InvalidAppEntry -> invalidEntry
        ClipboardImportFailure.InvalidAppUserId -> invalidUserId
        ClipboardImportFailure.UnsupportedAppMode -> unsupportedMode
        ClipboardImportFailure.UnsupportedFormat -> unsupportedFormat
        else -> unsupportedFormat
    }
}
