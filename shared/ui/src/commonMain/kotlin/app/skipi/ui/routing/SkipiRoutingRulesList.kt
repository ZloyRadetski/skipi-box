// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.routing_domain_policy
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.routing_empty
import app.skipi.ui.resources.routing_title
import features.routing.model.RouteRule
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

@OptIn(ExperimentalFoundationApi::class, ExperimentalScrollBarApi::class)
@Composable
fun SkipiRoutingRulesList(
    rules: List<RouteRule>,
    outboundLabels: Map<String, String>,
    domainStrategyOptions: List<String>,
    selectedDomainStrategy: Int,
    contentPadding: PaddingValues,
    onDomainStrategyChange: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onToggle: (RouteRule, Boolean) -> Unit,
    onEdit: (RouteRule) -> Unit,
    onDelete: (RouteRule) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        val fromIndex = from.index - 2
        val toIndex = to.index - 2
        if (fromIndex in rules.indices && toIndex in rules.indices) onMove(fromIndex, toIndex)
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item(key = "routing_policy") {
                SmallTitle(text = stringResource(Res.string.routing_domain_policy))
                SkipiRoutingPolicyCard(
                    options = domainStrategyOptions,
                    selectedIndex = selectedDomainStrategy,
                    onSelectedIndexChange = onDomainStrategyChange,
                )
            }
            item(key = "routing_rules_title") { SmallTitle(text = stringResource(Res.string.routing_title)) }
            items(rules, key = RouteRule::id) { rule ->
                ReorderableItem(
                    state = reorderableState,
                    key = rule.id,
                    enabled = rules.size > 1,
                ) { isDragging ->
                    SkipiRouteRuleCard(
                        rule = rule,
                        outboundLabel = outboundLabels[rule.outboundTag] ?: rule.outboundTag,
                        isDragging = isDragging,
                        onToggle = { onToggle(rule, it) },
                        onEdit = { onEdit(rule) },
                        onDelete = { onDelete(rule) },
                        dragModifier = Modifier.longPressDraggableHandle(enabled = rules.size > 1),
                    )
                }
            }
            if (rules.isEmpty()) item(key = "routing_empty") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = CardDefaults.defaultColors(),
                ) {
                    Column(Modifier.padding(16.dp)) { Text(stringResource(Res.string.routing_empty)) }
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

