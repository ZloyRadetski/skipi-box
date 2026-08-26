// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.basic.BasicComponentColors
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownColors
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.layout.BottomSheetDefaults
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import ui.LocalPopupColors
import ui.LocalPopupTextStyles

/**
 * Applies the app typography (font size scale, font weight and family) to miuix
 * window-level components.
 *
 * Their content is rendered in separate windows whose composition re-provides
 * [androidx.compose.ui.platform.LocalDensity] from the new window's resources,
 * so the app-wide density override does not reach them and `sp` values resolve
 * unscaled. Wrapping with pre-scaled text styles restores font size scaling;
 * themed weights and families ride along in the same styles.
 */
@Composable
fun AppMiuixWindowTheme(content: @Composable () -> Unit) {
    val popupTextStyles = LocalPopupTextStyles.current
    val popupColors = LocalPopupColors.current
    if (popupTextStyles != null || popupColors != null) {
        val currentColors = MiuixTheme.colorScheme
        val currentStyles = MiuixTheme.textStyles
        MiuixTheme(
            colors = popupColors ?: currentColors,
            textStyles = popupTextStyles ?: currentStyles,
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun AppWindowDialog(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color = DialogDefaults.titleColor(),
    summary: String? = null,
    summaryColor: Color = DialogDefaults.summaryColor(),
    backgroundColor: Color = DialogDefaults.backgroundColor(),
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = DialogDefaults.outsideMargin,
    insideMargin: DpSize = DialogDefaults.insideMargin,
    defaultWindowInsetsPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    AppMiuixWindowTheme {
        WindowDialog(
            show = show,
            modifier = modifier,
            title = title,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            backgroundColor = backgroundColor,
            enableWindowDim = enableWindowDim,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            outsideMargin = outsideMargin,
            insideMargin = insideMargin,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            content = {
                AppMiuixWindowTheme {
                    content()
                }
            },
        )
    }
}

@Composable
fun AppWindowBottomSheet(
    show: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null,
    startAction: (@Composable (() -> Unit))? = null,
    endAction: (@Composable (() -> Unit))? = null,
    backgroundColor: Color = BottomSheetDefaults.backgroundColor(),
    enableWindowDim: Boolean = true,
    cornerRadius: Dp = BottomSheetDefaults.cornerRadius,
    sheetMaxWidth: Dp = BottomSheetDefaults.maxWidth,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = BottomSheetDefaults.outsideMargin,
    insideMargin: DpSize = BottomSheetDefaults.insideMargin,
    defaultWindowInsetsPadding: Boolean = true,
    dragHandleColor: Color = BottomSheetDefaults.dragHandleColor(),
    allowDismiss: Boolean = true,
    enableNestedScroll: Boolean = true,
    content: @Composable () -> Unit,
) {
    AppMiuixWindowTheme {
        WindowBottomSheet(
            show = show,
            modifier = modifier,
            title = title,
            startAction = startAction,
            endAction = endAction,
            backgroundColor = backgroundColor,
            enableWindowDim = enableWindowDim,
            cornerRadius = cornerRadius,
            sheetMaxWidth = sheetMaxWidth,
            onDismissRequest = onDismissRequest,
            onDismissFinished = onDismissFinished,
            outsideMargin = outsideMargin,
            insideMargin = insideMargin,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            dragHandleColor = dragHandleColor,
            allowDismiss = allowDismiss,
            enableNestedScroll = enableNestedScroll,
            content = {
                AppMiuixWindowTheme {
                    content()
                }
            },
        )
    }
}

@Composable
fun AppWindowDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: (@Composable (() -> Unit))? = null,
    bottomAction: (@Composable (() -> Unit))? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    AppMiuixWindowTheme {
        WindowDropdownPreference(
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            modifier = modifier,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            dropdownColors = dropdownColors,
            startAction = startAction,
            bottomAction = bottomAction,
            insideMargin = insideMargin,
            maxHeight = maxHeight,
            enabled = enabled,
            showValue = showValue,
            onExpandedChange = onExpandedChange,
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}

@Composable
fun AppWindowDropdownPreference(
    title: String,
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: (@Composable (() -> Unit))? = null,
    bottomAction: (@Composable (() -> Unit))? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    collapseOnSelection: Boolean = entries.size <= 1,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    AppMiuixWindowTheme {
        WindowDropdownPreference(
            title = title,
            entries = entries,
            modifier = modifier,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            dropdownColors = dropdownColors,
            startAction = startAction,
            bottomAction = bottomAction,
            insideMargin = insideMargin,
            maxHeight = maxHeight,
            enabled = enabled,
            showValue = showValue,
            collapseOnSelection = collapseOnSelection,
            onExpandedChange = onExpandedChange,
        )
    }
}

@Composable
fun AppOverlayDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: (@Composable (() -> Unit))? = null,
    bottomAction: (@Composable (() -> Unit))? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    renderInRootScaffold: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    OverlayDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = title,
        modifier = modifier,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        dropdownColors = dropdownColors,
        startAction = startAction,
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        maxHeight = maxHeight,
        enabled = enabled,
        showValue = showValue,
        renderInRootScaffold = renderInRootScaffold,
        onExpandedChange = onExpandedChange,
        onSelectedIndexChange = onSelectedIndexChange,
    )
}

@Composable
fun AppOverlayDropdownPreference(
    title: String,
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: (@Composable (() -> Unit))? = null,
    bottomAction: (@Composable (() -> Unit))? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = entries.size <= 1,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    OverlayDropdownPreference(
        title = title,
        entries = entries,
        modifier = modifier,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        dropdownColors = dropdownColors,
        startAction = startAction,
        bottomAction = bottomAction,
        insideMargin = insideMargin,
        maxHeight = maxHeight,
        enabled = enabled,
        showValue = showValue,
        renderInRootScaffold = renderInRootScaffold,
        collapseOnSelection = collapseOnSelection,
        onExpandedChange = onExpandedChange,
    )
}
