// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.subscription

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.skipi.app.subscription.SubscriptionGroupListPresentationState
import app.skipi.app.subscription.resolveSubscriptionGroupUpdatePayload
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.components.DeleteConfirmationDialog
import app.skipi.ui.components.NavigationIcon
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageListPadding
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add

/** Shared subscription-group screen shell. Persistence, refresh and editor data remain host supplied. */
@Composable
fun <DeletionPayload> SubscriptionGroupListScreen(
    groups: List<SubscriptionGroupUiState>,
    title: String,
    subtitle: String,
    deletionTitle: String,
    padding: PaddingValues,
    isWideScreen: Boolean,
    confirmDeletion: Boolean,
    resolveGroupPayload: (groupId: Int) -> DeletionPayload?,
    onBack: () -> Unit,
    onToggle: (SubscriptionGroupUiState, Boolean) -> Unit,
    onUpdate: (groupId: Int, group: DeletionPayload, onFinished: () -> Unit) -> Unit,
    onDelete: (DeletionPayload) -> Unit,
    editorContent: @Composable (
        show: Boolean,
        editingGroupId: Int?,
        onDismissRequest: () -> Unit,
        onDismissFinished: () -> Unit,
        onStartUpdate: (groupId: Int, group: DeletionPayload?) -> Unit,
    ) -> Unit,
) {
    var presentation by remember { mutableStateOf(SubscriptionGroupListPresentationState<DeletionPayload>()) }
    val scrollBehavior = MiuixScrollBehavior()

    fun startUpdate(groupId: Int, groupOverride: DeletionPayload? = null) {
        if (groupId in presentation.updatingGroupIds) return
        val group = resolveSubscriptionGroupUpdatePayload(groupId, groupOverride, resolveGroupPayload) ?: return
        presentation = presentation.copy(updatingGroupIds = presentation.updatingGroupIds + groupId)
        onUpdate(groupId, group) {
            presentation = presentation.copy(updatingGroupIds = presentation.updatingGroupIds - groupId)
        }
    }

    Scaffold(
        containerColor = SkipiTheme.colors.background,
        topBar = {
            AdaptiveTopAppBar(
                title = title,
                subtitle = subtitle,
                isWideScreen = isWideScreen,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    NavigationIcon(
                        onClick = {
                            presentation = presentation.copy(editingGroupId = null, showGroupEditor = true)
                        },
                        imageVector = MiuixIcons.Add,
                    )
                },
            )
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        SubscriptionGroupListContent(
            groups = groups,
            updatingGroupIds = presentation.updatingGroupIds,
            contentPadding = pageListPadding(contentPadding),
            modifier = Modifier.fillMaxSize(),
            scrollModifier = Modifier.pageScrollModifiers(scrollBehavior),
            trackPadding = contentPadding,
            onToggle = onToggle,
            onUpdate = { group -> startUpdate(group.id) },
            onEdit = { group ->
                presentation = presentation.copy(editingGroupId = group.id, showGroupEditor = true)
            },
            onDelete = { group ->
                if (group.builtIn) return@SubscriptionGroupListContent
                val payload = resolveGroupPayload(group.id) ?: return@SubscriptionGroupListContent
                if (confirmDeletion) {
                    presentation = presentation.copy(pendingGroupDeletion = payload)
                } else {
                    onDelete(payload)
                }
            },
        )
        editorContent(
            presentation.showGroupEditor,
            presentation.editingGroupId,
            { presentation = presentation.copy(showGroupEditor = false) },
            { presentation = presentation.copy(editingGroupId = null) },
            ::startUpdate,
        )
        presentation.pendingGroupDeletion?.let { group ->
            DeleteConfirmationDialog(
                show = true,
                title = deletionTitle,
                onDismissRequest = { presentation = presentation.copy(pendingGroupDeletion = null) },
                onConfirm = {
                    presentation = presentation.copy(pendingGroupDeletion = null)
                    onDelete(group)
                },
            )
        }
    }
}
