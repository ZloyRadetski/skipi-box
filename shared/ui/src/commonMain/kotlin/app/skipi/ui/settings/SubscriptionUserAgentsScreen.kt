// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import app.skipi.ui.components.StringListEditor
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.settings_user_agents
import app.skipi.ui.resources.settings_user_agents_description
import app.skipi.ui.resources.settings_user_agents_empty
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

data class SubscriptionUserAgentsState(
    val values: List<String>,
)

/** Platform-neutral subscription User-Agent editor. The host owns persistence and navigation. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SubscriptionUserAgentsScreen(
    state: SubscriptionUserAgentsState,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onValuesChange: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            val title = stringResource(Res.string.settings_user_agents)
            val navigateBack: @Composable () -> Unit = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            }
            SmallTopAppBar(
                title = title,
                scrollBehavior = scrollBehavior,
                color = Color.Transparent,
                defaultWindowInsetsPadding = !isWideScreen,
                navigationIcon = navigateBack,
            )
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + if (isWideScreen) padding.calculateTopPadding() else 0.dp,
            start = innerPadding.calculateStartPadding(layoutDirection) + padding.calculateStartPadding(layoutDirection),
            end = innerPadding.calculateEndPadding(layoutDirection) + padding.calculateEndPadding(layoutDirection),
            bottom = innerPadding.calculateBottomPadding() + padding.calculateBottomPadding() + 12.dp,
        )
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "subscription_user_agents_title") {
                    SmallTitle(text = stringResource(Res.string.settings_user_agents))
                }
                item(key = "subscription_user_agents_editor") {
                    StringListEditor(
                        editorKey = "subscription-user-agents-page",
                        title = stringResource(Res.string.settings_user_agents),
                        values = state.values,
                        onValuesChange = onValuesChange,
                        emptyText = stringResource(Res.string.settings_user_agents_empty),
                        description = stringResource(Res.string.settings_user_agents_description),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
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
