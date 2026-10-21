// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.configs_proxy_groups_summary
import app.skipi.ui.resources.configs_proxy_groups_title
import app.skipi.ui.resources.configs_resources
import app.skipi.ui.resources.configs_resources_summary
import app.skipi.ui.resources.configs_rule_summary
import app.skipi.ui.resources.configs_rules_title
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import org.jetbrains.compose.resources.stringResource

/** Shared Routing section and its Resources, Rules, Proxy Groups navigation cards. */
@Composable
fun SkipiTrafficConfigRoutingSectionScreen(
    profile: TrafficConfigState,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onResources: () -> Unit,
    onRules: () -> Unit,
    onProxyGroups: () -> Unit,
    platformBackHandler: @Composable (onBack: () -> Unit) -> Unit = {},
) {
    platformBackHandler(onBack)
    val analysis = remember(profile.rawConfig) { profile.rawConfig.analyzeShadowrocketConfig() }
    SkipiTrafficConfigFullScreenScaffold(
        title = stringResource(Res.string.configs_rules_title),
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = stringResource(Res.string.common_save),
        onBack = onBack,
        onSave = onBack,
    ) { _, listPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("resources") {
                SkipiTrafficConfigEditorGroupCard(
                    title = stringResource(Res.string.configs_resources),
                    summary = stringResource(Res.string.configs_resources_summary),
                    onClick = onResources,
                )
            }
            item("rules") {
                SkipiTrafficConfigEditorGroupCard(
                    title = stringResource(Res.string.configs_rules_title),
                    summary = stringResource(Res.string.configs_rule_summary, analysis.rules.count { !it.isFinal }),
                    onClick = onRules,
                )
            }
            item("groups") {
                SkipiTrafficConfigEditorGroupCard(
                    title = stringResource(Res.string.configs_proxy_groups_title),
                    summary = stringResource(Res.string.configs_proxy_groups_summary),
                    onClick = onProxyGroups,
                )
            }
        }
    }
}
