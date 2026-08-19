// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.LocalAppStateStore
import app.ProxyServerLatencyTesting
import app.R
import app.collectAppState
import app.modes.ProxyServerListSortDefault
import app.modes.ProxyServerListSortLatency
import app.modes.ProxyServerListSortName
import features.proxy.server.display.ProtocolColorUtils
import ui.keyColorFor
import ui.resolveSystemAccentColor
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowCascadingListPopup
import ui.AppTheme
import ui.icons.StaticHourglass
import ui.isInDarkTheme
import ui.components.IconDropdownMenu
import ui.components.IconDropdownMenuEntry
import ui.components.draggedCardShadow
import features.proxy.server.display.CountryFlagUtils
import kotlin.math.floor
import kotlin.math.roundToInt

private val proxyServerLatencyNumberRegex = Regex("""\d+""")
private val ProxyServerListFloatingToolbarButtonSize = 52.dp
private val ProxyServerListFloatingToolbarVerticalPadding = 8.dp
private val ProxyServerListFloatingToolbarBottomSpacing = 16.dp
private val ProxyServerListFloatingToolbarContentGap = 12.dp
private val ProxyServerListGroupTabHeight = 36.dp
private val ProxyServerListGroupTabSpacing = 8.dp
private val ProxyServerListCompactCardHeight = 66.dp
private val ProxyServerListCompactCardPadding = 8.dp
internal val ProxyServerListFloatingToolbarReservedBottomPadding =
    ProxyServerListFloatingToolbarButtonSize +
        ProxyServerListFloatingToolbarVerticalPadding +
        ProxyServerListFloatingToolbarVerticalPadding +
        ProxyServerListFloatingToolbarBottomSpacing +
        ProxyServerListFloatingToolbarContentGap

private data class ProxyServerListGroupTabBounds(
    val leftPx: Int,
    val widthPx: Int,
)

private fun linearInterpolate(start: Int, end: Int, fraction: Float): Int {
    return (start + (end - start) * fraction).roundToInt()
}

@Composable
internal fun ProxyServerListGroupTabs(
    groups: List<ProxyServerListGroupTabUi>,
    selectedGroupId: Int,
    pagerPositionProvider: () -> Float,
    onGroupSelected: (Int) -> Unit,
    onGroupMove: (groupId: Int, offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    val tabScrollState = rememberScrollState()
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var tabBounds by remember { mutableStateOf<Map<Int, ProxyServerListGroupTabBounds>>(emptyMap()) }
    var reorderGroupId by remember { mutableStateOf<Int?>(null) }
    val selectedIndex = groups.indexOfFirst { group -> group.id == selectedGroupId }
    val selectedBounds = tabBounds[selectedGroupId]

    LaunchedEffect(selectedIndex, selectedBounds, viewportWidthPx) {
        if (selectedIndex < 0 || selectedBounds == null || viewportWidthPx <= 0) return@LaunchedEffect
        val visibleStart = tabScrollState.value
        val visibleEnd = visibleStart + viewportWidthPx
        val tabStart = selectedBounds.leftPx
        val tabEnd = selectedBounds.leftPx + selectedBounds.widthPx
        val targetScroll = when {
            tabStart < visibleStart -> tabStart
            tabEnd > visibleEnd -> tabEnd - viewportWidthPx
            else -> visibleStart
        }.coerceIn(0, tabScrollState.maxValue)
        if (targetScroll != visibleStart) {
            tabScrollState.animateScrollTo(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .onSizeChanged { size -> viewportWidthPx = size.width }
            .horizontalScroll(tabScrollState),
    ) {
        Box(
            modifier = Modifier
                .height(ProxyServerListGroupTabHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(AppTheme.colors.accent)
                .layout { measurable, constraints ->
                    val pagerPosition = pagerPositionProvider().coerceIn(0f, groups.lastIndex.toFloat())
                    val startIndex = floor(pagerPosition).toInt().coerceIn(0, groups.lastIndex)
                    val endIndex = (startIndex + 1).coerceAtMost(groups.lastIndex)
                    val indicatorStartBounds = tabBounds[groups[startIndex].id]
                    val indicatorEndBounds = tabBounds[groups[endIndex].id] ?: indicatorStartBounds
                    val indicatorFraction = pagerPosition - startIndex
                    val indicatorLeftPx = if (indicatorStartBounds != null && indicatorEndBounds != null) {
                        linearInterpolate(
                            start = indicatorStartBounds.leftPx,
                            end = indicatorEndBounds.leftPx,
                            fraction = indicatorFraction,
                        )
                    } else {
                        selectedBounds?.leftPx ?: 0
                    }
                    val indicatorWidthPx = if (indicatorStartBounds != null && indicatorEndBounds != null) {
                        linearInterpolate(
                            start = indicatorStartBounds.widthPx,
                            end = indicatorEndBounds.widthPx,
                            fraction = indicatorFraction,
                        )
                    } else {
                        selectedBounds?.widthPx ?: 0
                    }

                    if (indicatorWidthPx <= 0) {
                        layout(0, 0) {}
                    } else {
                        val placeable = measurable.measure(
                            Constraints(
                                minWidth = indicatorWidthPx,
                                maxWidth = indicatorWidthPx,
                                minHeight = constraints.minHeight,
                                maxHeight = constraints.maxHeight,
                            ),
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(indicatorLeftPx, 0)
                        }
                    }
                },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(ProxyServerListGroupTabSpacing),
        ) {
            groups.forEach { group ->
                val selected = group.id == selectedGroupId
                val tabText = "${group.name} (${group.serverCount})"
                val interactionSource = remember(group.id) { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .height(ProxyServerListGroupTabHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onGroupSelected(group.id) },
                            onLongClick = {
                                if (group.id > features.subscription.DefaultSubscriptionGroupId) {
                                    reorderGroupId = group.id
                                }
                            },
                        )
                        .onGloballyPositioned { coordinates ->
                            val leftPx = coordinates.positionInParent().x.roundToInt()
                            val bounds = ProxyServerListGroupTabBounds(
                                leftPx = leftPx,
                                widthPx = coordinates.size.width,
                            )
                            if (tabBounds[group.id] != bounds) {
                                tabBounds = tabBounds + (group.id to bounds)
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tabText,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        reorderGroupId?.let { groupId ->
            val reorderableIds = groups
                .map { group -> group.id }
                .filter { id -> id > features.subscription.DefaultSubscriptionGroupId }
            val currentIndex = reorderableIds.indexOf(groupId)
            val actions = buildList {
                if (currentIndex > 0) {
                    add(
                        DropdownItem(
                            text = stringResource(R.string.subscription_move_left),
                            onClick = {
                                reorderGroupId = null
                                onGroupMove(groupId, -1)
                            },
                        ),
                    )
                }
                if (currentIndex >= 0 && currentIndex < reorderableIds.lastIndex) {
                    add(
                        DropdownItem(
                            text = stringResource(R.string.subscription_move_right),
                            onClick = {
                                reorderGroupId = null
                                onGroupMove(groupId, 1)
                            },
                        ),
                    )
                }
            }
            if (actions.isNotEmpty()) {
                WindowCascadingListPopup(
                    show = true,
                    entries = listOf(DropdownEntry(items = actions)),
                    popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                    alignment = PopupPositionProvider.Align.TopEnd,
                    onDismissRequest = { reorderGroupId = null },
                )
            } else {
                reorderGroupId = null
            }
        }
    }
}

@Composable
internal fun ProxyServerListAddMenu(
    onAction: (ProxyServerListAddAction) -> Unit,
) {
    IconDropdownMenu(
        imageVector = MiuixIcons.Add,
        contentDescription = stringResource(R.string.proxy_server_list_add),
        entries = proxyServerListAddMenuEntries(),
        onAction = onAction,
    )
}

@Composable
internal fun ProxyServerListToolsMenu(
    sort: Int,
    onAction: (ProxyServerListToolAction) -> Unit,
) {
    IconDropdownMenu(
        imageVector = MiuixIcons.More,
        contentDescription = stringResource(R.string.proxy_server_list_more),
        entries = proxyServerListToolMenuEntries(sort = sort),
        onAction = onAction,
    )
}

@Composable
internal fun ProxyServerListSearchBar(
    searchValue: String,
    onSearchValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchBar(
        modifier = modifier,
        inputField = {
            InputField(
                query = searchValue,
                onQueryChange = onSearchValueChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                label = stringResource(R.string.proxy_server_list_search_label),
            )
        },
        expanded = false,
        onExpandedChange = {},
    ) {}
}

@Composable
internal fun ProxyServerListItemCard(
    latency: String,
    displayText: ProxyServerListItemDisplayText,
    selected: Boolean,
    onSelect: () -> Unit,
    copyActions: List<ProxyServerListCopyAction>,
    onCopyAction: (ProxyServerListCopyAction) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    groupName: String? = null,
    compact: Boolean = false,
    inSubscriptionGroup: Boolean = false,
    isDragging: Boolean = false,
    dragModifier: Modifier = Modifier,
    isStrategyGroup: Boolean = false,
    activeMemberFlag: String? = null,
) {
    val latencyText = latency.trim()
    if (compact) {
        ProxyServerListCompactItemCard(
            latencyText = latencyText,
            displayText = displayText,
            selected = selected,
            onSelect = onSelect,
            copyActions = copyActions,
            onCopyAction = onCopyAction,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = modifier,
            isDragging = isDragging,
            dragModifier = dragModifier,
            inSubscriptionGroup = inSubscriptionGroup,
            isStrategyGroup = isStrategyGroup,
            activeMemberFlag = activeMemberFlag,
        )
    } else {
        ProxyServerListExpandedItemCard(
            latencyText = latencyText,
            displayText = displayText,
            selected = selected,
            onSelect = onSelect,
            copyActions = copyActions,
            onCopyAction = onCopyAction,
            onEdit = onEdit,
            onDelete = onDelete,
            modifier = modifier,
            groupName = groupName,
            isDragging = isDragging,
            dragModifier = dragModifier,
            isStrategyGroup = isStrategyGroup,
            activeMemberFlag = activeMemberFlag,
        )
    }
}

@Composable
private fun ProxyServerListExpandedItemCard(
    latencyText: String,
    displayText: ProxyServerListItemDisplayText,
    selected: Boolean,
    onSelect: () -> Unit,
    copyActions: List<ProxyServerListCopyAction>,
    onCopyAction: (ProxyServerListCopyAction) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
    groupName: String?,
    isDragging: Boolean,
    dragModifier: Modifier,
    isStrategyGroup: Boolean,
    activeMemberFlag: String?,
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        animationSpec = folmeSpring(damping = 0.9f, response = 0.38f),
        label = "proxyServerDragScale",
    )
    val animatedShadowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = folmeSpring(damping = 0.9f, response = 0.38f),
        label = "proxyServerDragShadowAlpha",
    )
    val shadowColor = AppTheme.colors.onSurface.copy(alpha = 0.20f)
    val selectedShape = RoundedCornerShape(16.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 10.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .draggedCardShadow(
                alpha = animatedShadowAlpha,
                color = shadowColor,
            )
            .clip(selectedShape)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) AppTheme.colors.onSurface.copy(alpha = 0.16f) else Color.Transparent,
                shape = selectedShape,
            )
            .then(dragModifier),
        colors = CardDefaults.defaultColors(
            color = if (selected) {
                AppTheme.colors.accent
            } else {
                AppTheme.colors.surface
            },
        ),
        insideMargin = PaddingValues(14.dp),
        onClick = onSelect,
    ) {
        val selfFlag = remember(displayText.title) { CountryFlagUtils.extractLeadingCountryFlag(displayText.title) }
        val effectiveFlag = remember(isStrategyGroup, activeMemberFlag, selfFlag) {
            if (isStrategyGroup) {
                activeMemberFlag ?: selfFlag ?: "⚡"
            } else {
                selfFlag
            }
        }
        val cleanTitle = remember(displayText.title, selfFlag) {
            if (selfFlag != null) CountryFlagUtils.stripLeadingCountryFlag(displayText.title) else displayText.title
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CountryFlagBadge(
                    flag = effectiveFlag,
                    size = 34.dp,
                    shapeRadius = 8.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = displayText.summary,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (groupName != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = groupName,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProtocolChip(
                    text = displayText.protocol,
                    selected = selected,
                )
                if (latencyText == ProxyServerLatencyTesting) {
                    Spacer(Modifier.width(8.dp))
                    InfiniteProgressIndicator(
                        color = MiuixTheme.colorScheme.primary,
                        size = 14.dp,
                        strokeWidth = 2.dp,
                    )
                } else if (latencyText.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = latencyText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = proxyServerLatencyColor(latencyText),
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconDropdownMenu(
                    imageVector = MiuixIcons.Copy,
                    contentDescription = stringResource(R.string.common_share),
                    entries = proxyServerListCopyMenuEntries(copyActions),
                    onAction = onCopyAction,
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = stringResource(R.string.common_edit),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProxyServerListCompactItemCard(
    latencyText: String,
    displayText: ProxyServerListItemDisplayText,
    selected: Boolean,
    onSelect: () -> Unit,
    copyActions: List<ProxyServerListCopyAction>,
    onCopyAction: (ProxyServerListCopyAction) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier,
    isDragging: Boolean,
    dragModifier: Modifier,
    inSubscriptionGroup: Boolean,
    isStrategyGroup: Boolean,
    activeMemberFlag: String?,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    var actionMenuOffset by remember { mutableStateOf(IntOffset.Zero) }
    val hapticFeedback = LocalHapticFeedback.current
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        animationSpec = folmeSpring(damping = 0.9f, response = 0.38f),
        label = "proxyServerCompactDragScale",
    )
    val animatedShadowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = folmeSpring(damping = 0.9f, response = 0.38f),
        label = "proxyServerCompactDragShadowAlpha",
    )
    val shadowColor = AppTheme.colors.onSurface.copy(alpha = 0.20f)
    val selectedShape = RoundedCornerShape(13.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ProxyServerListCompactCardHeight)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .draggedCardShadow(
                alpha = animatedShadowAlpha,
                color = shadowColor,
            )
            .clip(selectedShape)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) AppTheme.colors.onSurface.copy(alpha = 0.16f) else Color.Transparent,
                shape = selectedShape,
            )
            .then(dragModifier),
    ) {
        Card(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(onSelect) {
                    detectTapGestures(
                        onTap = { onSelect() },
                        onLongPress = { offset ->
                            actionMenuOffset = IntOffset(
                                x = offset.x.roundToInt(),
                                y = offset.y.roundToInt(),
                            )
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            showActionMenu = true
                        },
                    )
                },
            colors = CardDefaults.defaultColors(
                color = if (selected) {
                    AppTheme.colors.accent
                } else if (inSubscriptionGroup) {
                    Color.Transparent
                } else {
                    AppTheme.colors.surface
                },
            ),
            insideMargin = PaddingValues(ProxyServerListCompactCardPadding),
        ) {
            val selfFlag = remember(displayText.title) { CountryFlagUtils.extractLeadingCountryFlag(displayText.title) }
            val effectiveFlag = remember(isStrategyGroup, activeMemberFlag, selfFlag) {
                if (isStrategyGroup) {
                    activeMemberFlag ?: selfFlag ?: "⚡"
                } else {
                    selfFlag
                }
            }
            val cleanTitle = remember(displayText.title, selfFlag) {
                if (selfFlag != null) CountryFlagUtils.stripLeadingCountryFlag(displayText.title) else displayText.title
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CountryFlagBadge(
                    flag = effectiveFlag,
                    size = 32.dp,
                    shapeRadius = 8.dp,
                )
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = cleanTitle,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProtocolChip(
                            text = displayText.protocol,
                            modifier = Modifier.weight(1f, fill = false),
                            compact = true,
                            selected = selected,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = displayText.summary,
                            modifier = Modifier.weight(1f),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (latencyText == ProxyServerLatencyTesting) {
                            Spacer(Modifier.width(8.dp))
                            InfiniteProgressIndicator(
                                color = MiuixTheme.colorScheme.primary,
                                size = 12.dp,
                                strokeWidth = 1.8.dp,
                            )
                        } else if (latencyText.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = latencyText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = proxyServerLatencyColor(latencyText),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (showActionMenu) {
            Box(
                modifier = Modifier
                    .offset { actionMenuOffset }
                    .size(1.dp),
            ) {
                ProxyServerListCardActionMenu(
                    show = true,
                    copyActions = copyActions,
                    onCopyAction = onCopyAction,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onDismissRequest = { showActionMenu = false },
                )
            }
        }
    }
}

@Composable
private fun proxyServerListCopyMenuEntries(
    actions: List<ProxyServerListCopyAction>,
): List<IconDropdownMenuEntry<ProxyServerListCopyAction>> {
    return actions.map { action ->
        IconDropdownMenuEntry(
            key = action,
            title = when (action) {
                ProxyServerListCopyAction.QrCode -> stringResource(R.string.proxy_server_copy_qr_code)
                ProxyServerListCopyAction.Url -> stringResource(R.string.proxy_server_copy_url)
                ProxyServerListCopyAction.FullJson -> stringResource(R.string.proxy_server_copy_full_json)
            },
            action = action,
        )
    }
}

@Composable
private fun ProxyServerListCardActionMenu(
    show: Boolean,
    copyActions: List<ProxyServerListCopyAction>,
    onCopyAction: (ProxyServerListCopyAction) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current

    WindowCascadingListPopup(
        show = show,
        entries = listOf(
            DropdownEntry(
                items = listOf(
                    DropdownItem(
                        text = stringResource(R.string.common_share),
                        children = proxyServerListCardCopyMenuItems(
                            copyActions = copyActions,
                            hapticFeedback = hapticFeedback,
                            onDismissRequest = onDismissRequest,
                            onCopyAction = onCopyAction,
                        ),
                    ),
                    DropdownItem(
                        text = stringResource(R.string.common_edit),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onDismissRequest()
                            onEdit()
                        },
                    ),
                    DropdownItem(
                        text = stringResource(R.string.common_delete),
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onDismissRequest()
                            onDelete()
                        },
                    ),
                ),
            ),
        ),
        popupPositionProvider = ListPopupDefaults.DropdownPositionProvider,
        alignment = PopupPositionProvider.Align.Start,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun proxyServerListCardCopyMenuItems(
    copyActions: List<ProxyServerListCopyAction>,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onDismissRequest: () -> Unit,
    onCopyAction: (ProxyServerListCopyAction) -> Unit,
): List<DropdownItem> {
    return copyActions.map { action ->
        DropdownItem(
            text = when (action) {
                ProxyServerListCopyAction.QrCode -> stringResource(R.string.proxy_server_copy_qr_code)
                ProxyServerListCopyAction.Url -> stringResource(R.string.proxy_server_copy_url)
                ProxyServerListCopyAction.FullJson -> stringResource(R.string.proxy_server_copy_full_json)
            },
            onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                onDismissRequest()
                onCopyAction(action)
            },
        )
    }
}

@Composable
internal fun ProxyServerListFloatingToolbar(
    running: Boolean,
    serviceOperationInProgress: Boolean,
    bottomPadding: Dp,
    showPingAction: Boolean = true,
    onToggleRunning: () -> Unit,
    onRealConnectionTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appState by LocalAppStateStore.current.collectAppState()
    val accentTone = remember(appState.seedIndex, appState.customMaterialYouSeed, appState.customAccentColor, context) {
        appState.customAccentColor?.let { Color(it) } ?: (keyColorFor(appState.seedIndex, appState.customMaterialYouSeed) ?: resolveSystemAccentColor(context))
    }
    val fabColor = accentTone
    val fabIconTint = Color.White

    Box(
        modifier = modifier.padding(
            end = 20.dp,
            bottom = bottomPadding + ProxyServerListFloatingToolbarBottomSpacing,
        ),
    ) {
        FloatingToolbar(
            color = fabColor,
            cornerRadius = 32.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = ProxyServerListFloatingToolbarVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showPingAction) {
                    AnimatedVisibility(
                        visible = running,
                        enter = slideInHorizontally(initialOffsetX = { width -> width }) +
                            expandHorizontally(expandFrom = Alignment.End),
                        exit = slideOutHorizontally(targetOffsetX = { width -> width }) +
                            shrinkHorizontally(shrinkTowards = Alignment.End),
                    ) {
                        IconButton(
                            modifier = Modifier.size(ProxyServerListFloatingToolbarButtonSize),
                            onClick = onRealConnectionTest,
                        ) {
                            StaticHourglass(
                                modifier = Modifier.size(ProxyServerListFloatingToolbarButtonSize),
                                color = fabIconTint,
                                size = 26.dp,
                            )
                        }
                    }
                }
                IconButton(
                    modifier = Modifier.size(ProxyServerListFloatingToolbarButtonSize),
                    onClick = {
                        if (!serviceOperationInProgress) {
                            onToggleRunning()
                        }
                    },
                ) {
                    Icon(
                        modifier = Modifier.size(26.dp),
                        imageVector = if (running) MiuixIcons.Pause else MiuixIcons.Play,
                        contentDescription = if (running) {
                            stringResource(R.string.proxy_server_list_stop_proxy)
                        } else {
                            stringResource(R.string.proxy_server_list_start_proxy)
                        },
                        tint = fabIconTint.copy(
                            alpha = if (serviceOperationInProgress) 0.45f else 1f,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProxyServerListEmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun proxyServerLatencyColor(text: String): Color {
    val latency = proxyServerLatencyNumberRegex.find(text)?.value?.toIntOrNull()
    val darkTheme = isInDarkTheme()
    val appState by LocalAppStateStore.current.collectAppState()
    val fastColor = appState.customPingFastColor?.let { Color(it) } ?: (if (darkTheme) Color(0xFF6BD58A) else Color(0xFF128A3C))
    val mediumColor = appState.customPingMediumColor?.let { Color(it) } ?: (if (darkTheme) Color(0xFFFFC857) else Color(0xFFD18A00))
    val slowColor = appState.customPingSlowColor?.let { Color(it) } ?: (if (darkTheme) Color(0xFFFF9B63) else Color(0xFFE06400))
    val errorColor = appState.customStatusStoppedColor?.let { Color(it) } ?: MiuixTheme.colorScheme.error

    return when {
        latency == null -> errorColor
        latency < 100 -> fastColor
        latency < 200 -> mediumColor
        latency < 400 -> slowColor
        else -> errorColor
    }
}

@Composable
private fun ProtocolChip(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    selected: Boolean = false,
) {
    val darkTheme = isInDarkTheme()
    val appState by LocalAppStateStore.current.collectAppState()
    val chipColor = ProtocolColorUtils.resolveProtocolColor(text, appState, darkTheme)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                chipColor.copy(alpha = if (selected) 0.22f else 0.12f),
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun proxyServerListAddMenuEntries() = listOf(
    proxyServerListAddMenuEntry(stringResource(R.string.proxy_server_list_scan_qr_code), ProxyServerListAddAction.ScanQrCode),
    proxyServerListAddMenuEntry(stringResource(R.string.proxy_server_list_import_clipboard), ProxyServerListAddAction.Clipboard),
    proxyServerListAddMenuEntry(stringResource(R.string.proxy_server_list_import_file), ProxyServerListAddAction.File),
    IconDropdownMenuEntry(
        key = "manual_input",
        title = stringResource(R.string.proxy_server_list_manual_input),
        children = proxyServerListManualInputMenuEntries(),
    ),
    proxyServerListAddMenuEntry(
        stringResource(R.string.proxy_server_list_add_strategy_group),
        ProxyServerListAddAction.StrategyGroup,
    ),
    proxyServerListAddMenuEntry(stringResource(R.string.proxy_server_list_add_chain_proxy), ProxyServerListAddAction.ChainProxy),
    proxyServerListAddMenuEntry(stringResource(R.string.proxy_server_list_add_custom), ProxyServerListAddAction.Custom),
)

@Composable
private fun proxyServerListManualInputMenuEntries() = listOf(
    ProxyServerListMenuEntry(stringResource(R.string.proxy_server_list_add_http), ProxyServerListAddAction.HTTP),
    ProxyServerListMenuEntry(stringResource(R.string.proxy_server_list_add_vmess), ProxyServerListAddAction.VMess),
    ProxyServerListMenuEntry(stringResource(R.string.proxy_server_list_add_vless), ProxyServerListAddAction.VLESS),
    ProxyServerListMenuEntry(stringResource(R.string.proxy_server_list_add_trojan), ProxyServerListAddAction.Trojan),
    ProxyServerListMenuEntry(stringResource(R.string.proxy_server_list_add_shadowsocks), ProxyServerListAddAction.Shadowsocks),
    ProxyServerListMenuEntry(stringResource(R.string.proxy_server_list_add_socks), ProxyServerListAddAction.Socks),
    ProxyServerListMenuEntry(stringResource(R.string.proxy_server_list_add_hysteria2), ProxyServerListAddAction.Hysteria2),
    ProxyServerListMenuEntry(stringResource(R.string.proxy_server_list_add_wireguard), ProxyServerListAddAction.Wireguard),
).map { entry ->
    proxyServerListAddMenuEntry(entry.title, entry.action)
}

private fun proxyServerListAddMenuEntry(
    title: String,
    action: ProxyServerListAddAction,
) = IconDropdownMenuEntry(
    key = action,
    title = title,
    action = action,
)

@Composable
private fun proxyServerListToolMenuEntries(
    sort: Int,
): List<IconDropdownMenuEntry<ProxyServerListToolAction>> = listOf(
    proxyServerListToolMenuEntry(
        stringResource(R.string.proxy_server_list_restart_service),
        ProxyServerListToolAction.RestartService,
    ),
    proxyServerListToolMenuEntry(
        stringResource(R.string.proxy_server_list_update_subscriptions),
        ProxyServerListToolAction.UpdateSubscriptions,
    ),
    proxyServerListToolMenuEntry(
        stringResource(R.string.proxy_server_list_latency_test),
        ProxyServerListToolAction.TestLatency,
    ),
    proxyServerListToolMenuEntry(
        stringResource(R.string.proxy_server_list_real_connection_test),
        ProxyServerListToolAction.TestRealConnection,
    ),
    IconDropdownMenuEntry(
        key = "sort",
        title = stringResource(R.string.proxy_server_list_option_sort),
        children = listOf(
            proxyServerListToolMenuEntry(
                title = stringResource(R.string.proxy_server_list_option_sort_default),
                action = ProxyServerListToolAction.SetSortDefault,
                selected = sort == ProxyServerListSortDefault,
            ),
            proxyServerListToolMenuEntry(
                title = stringResource(R.string.proxy_server_list_option_sort_name),
                action = ProxyServerListToolAction.SetSortName,
                selected = sort == ProxyServerListSortName,
            ),
            proxyServerListToolMenuEntry(
                title = stringResource(R.string.proxy_server_list_option_sort_latency),
                action = ProxyServerListToolAction.SetSortLatency,
                selected = sort == ProxyServerListSortLatency,
            ),
        ),
    ),
    IconDropdownMenuEntry(
        key = "delete_proxy_servers",
        title = stringResource(R.string.proxy_server_list_delete_proxy_servers),
        children = listOf(
            proxyServerListToolMenuEntry(
                stringResource(R.string.proxy_server_list_delete_duplicates),
                ProxyServerListToolAction.DeleteDuplicateServers,
            ),
            proxyServerListToolMenuEntry(
                stringResource(R.string.proxy_server_list_delete_invalid),
                ProxyServerListToolAction.DeleteInvalidServers,
            ),
            proxyServerListToolMenuEntry(
                stringResource(R.string.proxy_server_list_delete_all),
                ProxyServerListToolAction.DeleteAllServers,
            ),
        ),
    ),
)

private fun proxyServerListToolMenuEntry(
    title: String,
    action: ProxyServerListToolAction,
    selected: Boolean = false,
) = IconDropdownMenuEntry(
    key = action,
    title = title,
    action = action,
    selected = selected,
)
