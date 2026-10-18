// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package app.skipi.ui.routing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import app.skipi.app.routing.RouteOutboundPickerProjection
import app.skipi.ui.components.NavigationIcon
import app.skipi.ui.layout.pageListPadding
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_rules_policy
import app.skipi.ui.resources.proxy_editor_strategy_group_no_servers
import app.skipi.ui.resources.routing_default_proxy_server
import app.skipi.ui.resources.routing_outbound_tag_label
import app.skipi.ui.resources.subscription_provider_servers
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import org.jetbrains.compose.resources.stringResource

/** Shared grouped picker; Android keeps only catalog, navigation, and painter adapters. */
@Composable
fun SkipiRouteOutboundSelectorScreen(
    projection: RouteOutboundPickerProjection,
    selectedTag: String,
    isPolicyMode: Boolean,
    padding: PaddingValues,
    isWideScreen: Boolean,
    subscriptionDetailsByGroupId: Map<Int, SkipiRoutingOutboundGroupDetails> = emptyMap(),
    fallbackPainter: Painter? = null,
    onBack: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val selectorState = remember { RouteOutboundSelectorState(selectedTag) }
    // Match remember(selectedTag) for the pending value without discarding expansion overrides.
    selectorState.updateSelectedTag(selectedTag)
    val listState = rememberLazyListState()
    val defaultProxyServerTemplate = stringResource(Res.string.routing_default_proxy_server)
    val groups = projection.groups.map { group ->
        SkipiRoutingOutboundGroup(
            key = group.key,
            title = group.title,
            countLabel = stringResource(Res.string.subscription_provider_servers, group.items.size),
            details = group.subscriptionGroupId?.let(subscriptionDetailsByGroupId::get),
            items = group.items.map { item ->
                presentRouteOutboundOption(item, defaultProxyServerTemplate)
            },
        )
    }

    SkipiRoutingPageScaffold(
        title = stringResource(
            if (isPolicyMode) Res.string.configs_rules_policy else Res.string.routing_outbound_tag_label,
        ),
        padding = padding,
        isWideScreen = isWideScreen,
        onBack = onBack,
        actions = {
            NavigationIcon(
                onClick = { onConfirm(selectorState.targetTag) },
                imageVector = MiuixIcons.Ok,
                contentDescription = stringResource(Res.string.common_save),
            )
        },
    ) { contentPadding, scrollBehavior ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.pageScrollModifiers(scrollBehavior),
                contentPadding = pageListPadding(contentPadding),
            ) {
                groups.forEach { group ->
                    val expanded = selectorState.isExpanded(
                        key = group.key,
                        initiallyExpandedGroupKey = projection.initiallyExpandedGroupKey,
                    )
                    item(key = "route-outbound-group-${group.key}") {
                        SkipiRoutingOutboundGroupHeader(
                            title = group.title,
                            countLabel = group.countLabel,
                            itemCount = group.items.size,
                            details = group.details,
                            expanded = expanded,
                            onExpandedChange = { selectorState.setExpanded(group.key, it) },
                        )
                    }
                    if (expanded) {
                        if (group.items.isEmpty()) {
                            item(key = "route-outbound-empty-${group.key}") {
                                SkipiRoutingOutboundEmptyGroupRow(
                                    message = stringResource(Res.string.proxy_editor_strategy_group_no_servers),
                                )
                            }
                        } else {
                            items(
                                items = group.items,
                                key = { item -> "route-outbound-entry-${group.key}-${item.key}" },
                                contentType = { "route-outbound-entry" },
                            ) { item ->
                                SkipiRoutingOutboundOptionRow(
                                    flag = item.flag,
                                    fallbackPainter = fallbackPainter,
                                    displayTitle = item.title,
                                    subtitle = item.subtitle,
                                    selected = item.tag == selectorState.targetTag,
                                    isLast = item.key == group.items.lastOrNull()?.key,
                                    onClick = { selectorState.select(item.tag) },
                                )
                            }
                        }
                    }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
}
