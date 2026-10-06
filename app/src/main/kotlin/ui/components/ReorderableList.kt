// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.SkipiReorderableLazyGridState as SharedGridState
import app.skipi.ui.components.SkipiReorderableLazyListState as SharedListState
import app.skipi.ui.components.longPressReorderDragHandle as sharedLongPressReorderDragHandle
import app.skipi.ui.components.moveItem as sharedMoveItem
import app.skipi.ui.components.rememberReorderableGridStateByKey as sharedRememberGridStateByKey
import app.skipi.ui.components.rememberReorderableLazyListContentPaddingWithoutTop as sharedRememberContentPadding
import app.skipi.ui.components.rememberReorderableListStateByKey as sharedRememberListStateByKey
import app.skipi.ui.components.rememberReorderableScrollThresholdPadding as sharedRememberThresholdPadding
import app.skipi.ui.components.rememberSkipiReorderableLazyGridState as sharedRememberIndexedGridState
import app.skipi.ui.components.rememberSkipiReorderableLazyListState as sharedRememberIndexedListState
import sh.calvin.reorderable.ReorderableCollectionItemScope

typealias SkipiReorderableLazyListState = SharedListState
typealias SkipiReorderableLazyGridState = SharedGridState

@Composable
internal fun rememberReorderableListStateByKey(lazyListState: LazyListState, scrollThresholdPadding: PaddingValues = PaddingValues(0.dp), onMove: (fromKey: Any, toKey: Any) -> Unit): SkipiReorderableLazyListState =
    sharedRememberListStateByKey(lazyListState, LocalHapticFeedback.current, scrollThresholdPadding, onMove)

@Composable
internal fun rememberReorderableGridStateByKey(lazyGridState: LazyGridState, scrollThresholdPadding: PaddingValues = PaddingValues(0.dp), onMove: (fromKey: Any, toKey: Any) -> Unit): SkipiReorderableLazyGridState =
    sharedRememberGridStateByKey(lazyGridState, LocalHapticFeedback.current, scrollThresholdPadding, onMove)

@Composable
internal fun rememberSkipiReorderableLazyListState(lazyListState: LazyListState, itemCount: Int, itemIndexOffset: Int = 0, scrollThresholdPadding: PaddingValues = PaddingValues(0.dp), onMove: (fromIndex: Int, toIndex: Int) -> Unit): SkipiReorderableLazyListState =
    sharedRememberIndexedListState(lazyListState, itemCount, LocalHapticFeedback.current, itemIndexOffset, scrollThresholdPadding, onMove)

@Composable
internal fun rememberSkipiReorderableLazyGridState(lazyGridState: LazyGridState, itemCount: Int, itemIndexOffset: Int = 0, scrollThresholdPadding: PaddingValues = PaddingValues(0.dp), onMove: (fromIndex: Int, toIndex: Int) -> Unit): SkipiReorderableLazyGridState =
    sharedRememberIndexedGridState(lazyGridState, itemCount, LocalHapticFeedback.current, itemIndexOffset, scrollThresholdPadding, onMove)

@Composable
internal fun rememberReorderableLazyListContentPaddingWithoutTop(listPadding: PaddingValues): PaddingValues =
    sharedRememberContentPadding(listPadding)

@Composable
internal fun rememberReorderableScrollThresholdPadding(top: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues =
    sharedRememberThresholdPadding(top, bottom)

internal fun Modifier.longPressReorderDragHandle(scope: ReorderableCollectionItemScope, enabled: Boolean, state: SkipiReorderableLazyListState, onDragStarted: (() -> Unit)? = null, onDragStopped: (() -> Unit)? = null): Modifier =
    sharedLongPressReorderDragHandle(scope, enabled, state, onDragStarted, onDragStopped)

internal fun Modifier.longPressReorderDragHandle(scope: ReorderableCollectionItemScope, enabled: Boolean, state: SkipiReorderableLazyGridState, onDragStarted: (() -> Unit)? = null, onDragStopped: (() -> Unit)? = null): Modifier =
    sharedLongPressReorderDragHandle(scope, enabled, state, onDragStarted, onDragStopped)

internal fun <T> List<T>.moveItem(fromIndex: Int, toIndex: Int): List<T> = sharedMoveItem(fromIndex, toIndex)
