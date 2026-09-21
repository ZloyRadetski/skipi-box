// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.animation.AnimatedContent
import ui.text.themedFontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
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
import app.skipi.ui.home.SkipiProxyServerCompactListCard
import app.skipi.ui.home.SkipiProxyServerCompactListCardColors
import app.skipi.ui.home.SkipiProxyServerCompactListCardState
import app.skipi.ui.home.SkipiProxyGroupPicker
import app.skipi.ui.home.SkipiProxyGroupPickerAction
import app.skipi.ui.home.SkipiProxyGroupPickerColors
import app.skipi.ui.home.SkipiProxyGroupPickerItem
import app.skipi.ui.home.SkipiProxyHomeSearchField
import app.skipi.ui.home.SkipiProxyProtocolChip
import app.skipi.ui.home.SkipiProxyServerExpandedListCard
import app.skipi.ui.home.SkipiProxyServerExpandedListCardColors
import app.skipi.ui.home.SkipiProxyServerExpandedListCardState
import app.skipi.ui.home.SkipiProxyTransportChip
import features.proxy.server.display.ProtocolColorUtils
import ui.StatusColorDefaults
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
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
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
import ui.components.AppCascadingListPopup
import ui.AppTheme
import ui.icons.StaticHourglass
import ui.isInDarkTheme
import ui.components.IconDropdownMenu
import ui.components.IconDropdownMenuEntry
import ui.components.draggedCardShadow
import features.proxy.server.display.CountryFlagUtils
import kotlin.math.roundToInt

private val proxyServerLatencyNumberRegex = Regex("""\d+""")
private val ProxyServerListFloatingToolbarButtonSize = 52.dp
private val ProxyServerListFloatingToolbarVerticalPadding = 8.dp
private val ProxyServerListFloatingToolbarBottomSpacing = 16.dp
private val ProxyServerListFloatingToolbarContentGap = 12.dp
private val ProxyServerListCompactCardHeight = 66.dp
internal val ProxyServerListFloatingToolbarReservedBottomPadding =
    ProxyServerListFloatingToolbarButtonSize +
        ProxyServerListFloatingToolbarVerticalPadding +
        ProxyServerListFloatingToolbarVerticalPadding +
        ProxyServerListFloatingToolbarBottomSpacing +
        ProxyServerListFloatingToolbarContentGap

/** Android haptic/reorder adapter around the shared group-picker surface. */
@Composable
internal fun SharedProxyServerListGroupSelector(
    groups: List<ProxyServerListGroupTabUi>,
    selectedGroupId: Int,
    onGroupSelected: (Int) -> Unit,
    onGroupMove: (groupId: Int, offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return
    val hapticFeedback = LocalHapticFeedback.current
    val moveLeftText = stringResource(R.string.subscription_move_left)
    val moveRightText = stringResource(R.string.subscription_move_right)
    val pickerItems = groups.map { group ->
        val serverCountText = stringResource(
            R.string.proxy_editor_strategy_group_servers_count,
            group.serverCount,
        )
        SkipiProxyGroupPickerItem(
            id = group.id.toString(),
            title = group.name,
            subtitle = serverCountText,
            pickerText = "${group.name} · $serverCountText",
        )
    }
    val contextActionsByGroupId = remember(groups, moveLeftText, moveRightText) {
        val reorderableIds = groups
            .map { group -> group.id }
            .filter { id -> id > features.subscription.DefaultSubscriptionGroupId }
        groups.associate { group ->
            val currentIndex = reorderableIds.indexOf(group.id)
            group.id.toString() to buildList {
                if (currentIndex > 0) {
                    add(SkipiProxyGroupPickerAction(id = "move_left", title = moveLeftText))
                }
                if (currentIndex >= 0 && currentIndex < reorderableIds.lastIndex) {
                    add(SkipiProxyGroupPickerAction(id = "move_right", title = moveRightText))
                }
            }
        }
    }

    SkipiProxyGroupPicker(
        groups = pickerItems,
        selectedGroupId = selectedGroupId.toString(),
        colors = SkipiProxyGroupPickerColors(
            surface = AppTheme.colors.surface,
            raisedSurface = AppTheme.colors.surfaceVariant,
            border = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f),
            accent = MiuixTheme.colorScheme.primary,
            text = MiuixTheme.colorScheme.onSurface,
            mutedText = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        ),
        onGroupSelected = selectGroup@{ groupIdText ->
            val groupId = groupIdText.toIntOrNull() ?: return@selectGroup
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
            onGroupSelected(groupId)
        },
        modifier = modifier,
        onPickerToggled = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
        },
        onSelectedGroupLongClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        contextActions = { groupId -> contextActionsByGroupId[groupId].orEmpty() },
        onContextAction = contextAction@{ groupIdText, actionId ->
            val groupId = groupIdText.toIntOrNull() ?: return@contextAction
            val offset = when (actionId) {
                "move_left" -> -1
                "move_right" -> 1
                else -> return@contextAction
            }
            onGroupMove(groupId, offset)
        },
    )
}
@Composable
internal fun ProxyServerListSearchBar(
    searchValue: String,
    onSearchValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SkipiProxyHomeSearchField(
        value = searchValue,
        onValueChange = onSearchValueChange,
        label = stringResource(R.string.proxy_server_list_search_label),
        modifier = modifier,
    )
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
    val actionButtonSize = if (isStrategyGroup) 32.dp else 40.dp
    val actionIconSize = if (isStrategyGroup) 19.dp else 24.dp
    val selfFlag = remember(displayText.title) { CountryFlagUtils.extractLeadingCountryFlag(displayText.title) }
    val effectiveFlag = remember(isStrategyGroup, activeMemberFlag, selfFlag) {
        if (isStrategyGroup) activeMemberFlag ?: selfFlag ?: "\u26A1" else selfFlag
    }
    val cleanTitle = remember(displayText.title, selfFlag) {
        if (selfFlag != null) CountryFlagUtils.stripLeadingCountryFlag(displayText.title) else displayText.title
    }
    val darkTheme = isInDarkTheme()
    val appState by LocalAppStateStore.current.collectAppState()
    val protocolColor = ProtocolColorUtils.resolveProtocolColor(displayText.protocol, appState, darkTheme)
    val transportTextColor = if (selected) {
        AppTheme.colors.onSurface.copy(alpha = 0.85f)
    } else if (darkTheme) {
        Color(0xFFB0BEC5)
    } else {
        Color(0xFF546E7A)
    }
    val transportContainerColor = if (selected) {
        AppTheme.colors.onSurface.copy(alpha = 0.12f)
    } else if (darkTheme) {
        Color(0xFF37474F).copy(alpha = 0.5f)
    } else {
        Color(0xFFECEFF1)
    }
    val latencyColor = if (latencyText.isNotEmpty() && latencyText != ProxyServerLatencyTesting) {
        proxyServerLatencyColor(latencyText)
    } else {
        Color.Transparent
    }

    SkipiProxyServerExpandedListCard(
        state = SkipiProxyServerExpandedListCardState(
            flag = effectiveFlag,
            title = cleanTitle,
            summary = displayText.summary,
            protocol = displayText.protocol,
            protocolColor = protocolColor,
            transport = displayText.transport,
            transportTextColor = transportTextColor,
            transportContainerColor = transportContainerColor,
            selected = selected,
            groupName = groupName,
            latencyText = latencyText.takeIf(String::isNotBlank),
            latencyTesting = latencyText == ProxyServerLatencyTesting,
            latencyColor = latencyColor,
            progressColor = MiuixTheme.colorScheme.primary,
            isStrategyGroup = isStrategyGroup,
            isDragging = isDragging,
        ),
        colors = SkipiProxyServerExpandedListCardColors(
            surface = AppTheme.colors.surface,
            selectedSurface = AppTheme.colors.accent,
            selectedBorder = AppTheme.colors.onSurface.copy(alpha = 0.16f),
        ),
        fallbackBadgePainter = painterResource(R.drawable.ic_globe),
        titleFontWeight = themedFontWeight(FontWeight.SemiBold),
        latencyFontWeight = themedFontWeight(FontWeight.Medium),
        onSelect = onSelect,
        actions = {
            IconDropdownMenu(
                imageVector = MiuixIcons.Copy,
                contentDescription = stringResource(R.string.common_share),
                entries = proxyServerListCopyMenuEntries(copyActions),
                onAction = onCopyAction,
                modifier = if (isStrategyGroup) Modifier.size(actionButtonSize) else Modifier,
            )
            IconButton(
                modifier = if (isStrategyGroup) Modifier.size(actionButtonSize) else Modifier,
                onClick = onEdit,
            ) {
                Icon(
                    modifier = if (isStrategyGroup) Modifier.size(actionIconSize) else Modifier,
                    imageVector = MiuixIcons.Edit,
                    contentDescription = stringResource(R.string.common_edit),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
            IconButton(
                modifier = if (isStrategyGroup) Modifier.size(actionButtonSize) else Modifier,
                onClick = onDelete,
            ) {
                Icon(
                    modifier = if (isStrategyGroup) Modifier.size(actionIconSize) else Modifier,
                    imageVector = MiuixIcons.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        },
        modifier = modifier,
        dragVisualModifier = Modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .draggedCardShadow(
                alpha = animatedShadowAlpha,
                color = shadowColor,
            ),
        dragModifier = dragModifier,
    )
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
    val selfFlag = remember(displayText.title) { CountryFlagUtils.extractLeadingCountryFlag(displayText.title) }
    val effectiveFlag = remember(isStrategyGroup, activeMemberFlag, selfFlag) {
        if (isStrategyGroup) activeMemberFlag ?: selfFlag ?: "\u26A1" else selfFlag
    }
    val cleanTitle = remember(displayText.title, selfFlag) {
        if (selfFlag != null) CountryFlagUtils.stripLeadingCountryFlag(displayText.title) else displayText.title
    }
    val darkTheme = isInDarkTheme()
    val appState by LocalAppStateStore.current.collectAppState()
    val protocolColor = ProtocolColorUtils.resolveProtocolColor(displayText.protocol, appState, darkTheme)
    val transportTextColor = if (selected) {
        AppTheme.colors.onSurface.copy(alpha = 0.85f)
    } else if (darkTheme) {
        Color(0xFFB0BEC5)
    } else {
        Color(0xFF546E7A)
    }
    val transportContainerColor = if (selected) {
        AppTheme.colors.onSurface.copy(alpha = 0.12f)
    } else if (darkTheme) {
        Color(0xFF37474F).copy(alpha = 0.5f)
    } else {
        Color(0xFFECEFF1)
    }
    val latencyColor = if (latencyText.isNotEmpty() && latencyText != ProxyServerLatencyTesting) {
        proxyServerLatencyColor(latencyText)
    } else {
        Color.Transparent
    }

    SkipiProxyServerCompactListCard(
        state = SkipiProxyServerCompactListCardState(
            flag = effectiveFlag,
            title = cleanTitle,
            summary = displayText.summary,
            protocol = displayText.protocol,
            protocolColor = protocolColor,
            transport = displayText.transport,
            transportTextColor = transportTextColor,
            transportContainerColor = transportContainerColor,
            selected = selected,
            inSubscriptionGroup = inSubscriptionGroup,
            latencyText = latencyText.takeIf(String::isNotBlank),
            latencyTesting = latencyText == ProxyServerLatencyTesting,
            latencyColor = latencyColor,
            progressColor = MiuixTheme.colorScheme.primary,
            isStrategyGroup = isStrategyGroup,
            isDragging = isDragging,
        ),
        colors = SkipiProxyServerCompactListCardColors(
            surface = AppTheme.colors.surface,
            selectedSurface = AppTheme.colors.accent,
            selectedBorder = AppTheme.colors.onSurface.copy(alpha = 0.16f),
        ),
        fallbackBadgePainter = painterResource(R.drawable.ic_globe),
        titleFontWeight = themedFontWeight(FontWeight.SemiBold),
        latencyFontWeight = themedFontWeight(FontWeight.Medium),
        onSelect = onSelect,
        onLongPress = { offset ->
            actionMenuOffset = IntOffset(
                x = offset.x.roundToInt(),
                y = offset.y.roundToInt(),
            )
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            showActionMenu = true
        },
        overlay = {
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
        },
        modifier = modifier,
        dragVisualModifier = Modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .draggedCardShadow(
                alpha = animatedShadowAlpha,
                color = shadowColor,
            ),
        dragModifier = dragModifier,
    )
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

    AppCascadingListPopup(
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
    showToggleAction: Boolean = true,
    showPingAction: Boolean = true,
    onToggleRunning: () -> Unit,
    onRealConnectionTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentTone = AppTheme.colors.accent
    val fabColor = accentTone
    val fabIconTint = Color.White

    val animatedFabColor by animateColorAsState(
        targetValue = if (running) fabColor else fabColor.copy(alpha = 0.95f),
        animationSpec = tween(300),
        label = "fab_color_anim",
    )

    val toggleScale by animateFloatAsState(
        targetValue = if (serviceOperationInProgress) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "fab_toggle_scale",
    )

    val shouldShow = showToggleAction || (showPingAction && running)
    if (!shouldShow) return

    Box(
        modifier = modifier.padding(
            end = 20.dp,
            bottom = bottomPadding + ProxyServerListFloatingToolbarBottomSpacing,
        ),
    ) {
        FloatingToolbar(
            color = animatedFabColor,
            cornerRadius = 32.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = ProxyServerListFloatingToolbarVerticalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showPingAction) {
                    if (showToggleAction) {
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
                    } else if (running) {
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
                if (showToggleAction) {
                    val isConnecting = serviceOperationInProgress && !running
                    IconButton(
                        modifier = Modifier
                            .size(ProxyServerListFloatingToolbarButtonSize)
                            .graphicsLayer {
                                scaleX = toggleScale
                                scaleY = toggleScale
                            },
                        onClick = {
                            if (!serviceOperationInProgress) {
                                onToggleRunning()
                            }
                        },
                    ) {
                        AnimatedContent(
                            targetState = when {
                                running -> 2
                                isConnecting -> 1
                                else -> 0
                            },
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220, delayMillis = 40)) + scaleIn(initialScale = 0.65f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)))
                                    .togetherWith(fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.65f))
                            },
                            label = "fab_play_pause_anim",
                        ) { buttonState ->
                            when (buttonState) {
                                1 -> ui.icons.AnimatedHourglassIcon(
                                    color = fabIconTint,
                                    isPinging = true,
                                    size = 24.dp,
                                )
                                2 -> Icon(
                                    modifier = Modifier.size(26.dp),
                                    imageVector = MiuixIcons.Pause,
                                    contentDescription = stringResource(R.string.proxy_server_list_stop_proxy),
                                    tint = fabIconTint,
                                )
                                else -> Icon(
                                    modifier = Modifier.size(26.dp),
                                    imageVector = MiuixIcons.Play,
                                    contentDescription = stringResource(R.string.proxy_server_list_start_proxy),
                                    tint = fabIconTint,
                                )
                            }
                        }
                    }
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
internal fun proxyServerLatencyColor(text: String): Color {
    val latency = proxyServerLatencyNumberRegex.find(text)?.value?.toIntOrNull()
    val darkTheme = isInDarkTheme()
    val appState by LocalAppStateStore.current.collectAppState()
    val fastColor = appState.customPingFastColor?.let { Color(it) } ?: StatusColorDefaults.pingFast(darkTheme)
    val mediumColor = appState.customPingMediumColor?.let { Color(it) } ?: StatusColorDefaults.pingMedium(darkTheme)
    val slowColor = appState.customPingSlowColor?.let { Color(it) } ?: StatusColorDefaults.pingSlow(darkTheme)
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
    SkipiProxyProtocolChip(
        text = text,
        chipColor = chipColor,
        modifier = modifier,
        compact = compact,
        selected = selected,
        fontWeight = themedFontWeight(FontWeight.SemiBold),
    )
}

@Composable
private fun TransportChip(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    selected: Boolean = false,
) {
    val darkTheme = isInDarkTheme()
    val chipColor = if (selected) {
        AppTheme.colors.onSurface.copy(alpha = 0.85f)
    } else {
        if (darkTheme) Color(0xFFB0BEC5) else Color(0xFF546E7A)
    }
    val containerColor = if (selected) {
        AppTheme.colors.onSurface.copy(alpha = 0.12f)
    } else if (darkTheme) {
        Color(0xFF37474F).copy(alpha = 0.5f)
    } else {
        Color(0xFFECEFF1)
    }
    SkipiProxyTransportChip(
        text = text,
        textColor = chipColor,
        containerColor = containerColor,
        modifier = modifier,
        compact = compact,
        fontWeight = themedFontWeight(FontWeight.Medium),
    )
}
