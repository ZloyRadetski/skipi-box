// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.configs_edit
import app.skipi.ui.resources.configs_proxy_groups_add
import app.skipi.ui.resources.configs_proxy_groups_empty
import app.skipi.ui.resources.configs_proxy_groups_summary
import app.skipi.ui.resources.configs_proxy_groups_title
import features.config.ShadowrocketPolicyGroup
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import org.jetbrains.compose.resources.stringResource

/** Shared full-screen editor for the proxy groups stored in one traffic profile. */
@Composable
fun SkipiTrafficConfigProxyGroupsEditorScreen(
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ShadowrocketPolicyGroup) -> Unit,
    platformBackHandler: @Composable (onBack: () -> Unit) -> Unit = {},
    statusContent: (@Composable () -> Unit)? = null,
) {
    platformBackHandler(onBack)
    val groups = remember(profile.rawConfig) {
        profile.rawConfig.analyzeShadowrocketConfig().proxyGroups
    }
    SkipiTrafficConfigFullScreenScaffold(
        title = stringResource(Res.string.configs_proxy_groups_title),
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = stringResource(Res.string.common_save),
        onBack = onBack,
        onSave = onBack,
    ) { _, listPadding, scrollBehavior ->
        SkipiTrafficConfigProxyGroups(
            groups = groups,
            contentPadding = listPadding,
            modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
            summary = stringResource(Res.string.configs_proxy_groups_summary),
            addLabel = stringResource(Res.string.configs_proxy_groups_add),
            emptyLabel = stringResource(Res.string.configs_proxy_groups_empty),
            editContentDescription = stringResource(Res.string.configs_edit),
            deleteContentDescription = stringResource(Res.string.common_delete),
            onAddGroup = onAdd,
            onEditGroup = onEdit,
            onDeleteGroup = { group ->
                controller.deleteProxyGroup(profile, group.lineNumber)
            },
            statusContent = statusContent,
        )
    }
}
