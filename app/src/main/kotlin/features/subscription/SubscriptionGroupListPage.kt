// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import app.AppServices
import app.AppState
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.SubscriptionGroupState
import app.skipi.app.store.SharedApplicationAction
import app.collectAppState
import app.skipi.ui.subscription.SubscriptionGroupListScreen
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import app.R
import data.AndroidAppStateStore
import features.proxy.server.model.Custom
import features.proxy.server.usecase.applyProxySubscriptionUpdates
import features.subscription.usecase.subscriptionUpdateMessage
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions
import kotlinx.coroutines.launch
import ui.text.formatTemplate

@Composable
fun SubscriptionGroupListPage(
    padding: PaddingValues,
) {
    val navigator = LocalNavigator.current
    val stateStore = LocalAppStateStore.current
    val services = LocalAppServices.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val isWideScreen = LocalIsWideScreen.current
    val scope = rememberCoroutineScope()
    val groups = appState.subscriptionGroups
    val subscriptionUpdateResultTemplate = stringResource(R.string.proxy_server_list_subscription_update_result)
    val subscriptionUpdateResultWithFailedTemplate =
        stringResource(R.string.proxy_server_list_subscription_update_result_with_failed)
    val invalidSubscriptionUrlMessage = stringResource(R.string.subscription_invalid_url)
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    val groupCountSubtitle = stringResource(R.string.subscription_group_list_count)
        .formatTemplate("count" to groups.size)

    fun saveGroup(
        group: SubscriptionGroupState,
        isNew: Boolean,
        startUpdate: (groupId: Int, group: SubscriptionGroupState?) -> Unit,
    ) {
        val targetGroup = if (isNew) {
            group.copy(id = appState.nextSubscriptionGroupId)
        } else {
            group
        }
        services.sharedApplicationStore.dispatch(
            SharedApplicationAction.UpdateProxyServers { servers ->
                servers.map { record ->
                    val server = record.server
                    if (record.sourceSubscriptionId == targetGroup.id && server is Custom) {
                        record.copy(server = server.copy(overrideInboundAndDns = targetGroup.autoOverrideRules))
                    } else {
                        record
                    }
                }
            },
        )
        updateAppState { state ->
            if (isNew) {
                state.copy(
                    subscriptionGroups = state.subscriptionGroups + targetGroup,
                    nextSubscriptionGroupId = state.nextSubscriptionGroupId + 1,
                )
            } else {
                state.copy(
                    subscriptionGroups = state.subscriptionGroups.map {
                        if (it.id == group.id) targetGroup else it
                    },
                )
            }
        }
        if (isNew && targetGroup.url.isNotBlank() && targetGroup.enabled) {
            startUpdate(targetGroup.id, targetGroup)
        }
    }

    fun deleteGroup(group: SubscriptionGroupState) {
        if (group.builtIn) return
        services.sharedApplicationStore.dispatch(SharedApplicationAction.RemoveSubscription(group.id))
    }

    SubscriptionGroupListScreen(
        groups = groups.map { it.toSubscriptionGroupUiState(defaultGroupName) },
        title = stringResource(R.string.subscription_group_list_title),
        subtitle = groupCountSubtitle,
        deletionTitle = stringResource(R.string.deletion_confirmation_delete_subscription_group),
        padding = padding,
        isWideScreen = isWideScreen,
        confirmDeletion = appState.enableDeletionConfirmation,
        resolveGroupPayload = { groupId -> groups.firstOrNull { it.id == groupId } },
        onBack = { navigator.pop() },
        onToggle = { edited, enabled ->
            updateAppState { state ->
                state.copy(subscriptionGroups = state.subscriptionGroups.map {
                    if (it.id == edited.id) it.copy(enabled = enabled) else it
                })
            }
        },
        onUpdate = { _, group, onFinished ->
            updateSubscriptionGroup(
                group = group,
                stateStore = stateStore,
                services = services,
                updateAppState = updateAppState,
                successTemplate = subscriptionUpdateResultTemplate,
                failedTemplate = subscriptionUpdateResultWithFailedTemplate,
                onFinished = onFinished,
            )
        },
        onDelete = ::deleteGroup,
        editorContent = { show, editingGroupId, onDismissRequest, onDismissFinished, onStartUpdate ->
            val editingGroup = editingGroupId?.let { id -> groups.firstOrNull { it.id == id } }
            SubscriptionGroupEditorDialog(
                show = show,
                group = editingGroup,
                nextGroupId = appState.nextSubscriptionGroupId,
                onDismissRequest = onDismissRequest,
                onDismissFinished = onDismissFinished,
                onSave = { edited, isNew -> saveGroup(edited, isNew, onStartUpdate) },
                onDelete = ::deleteGroup,
                onInvalidUrl = {
                    scope.launch { services.tipNotifier.show(invalidSubscriptionUrlMessage) }
                },
            )
        },
    )
}

private fun updateSubscriptionGroup(
    group: SubscriptionGroupState,
    stateStore: AndroidAppStateStore,
    services: AppServices,
    updateAppState: ((AppState) -> AppState) -> Unit,
    successTemplate: String,
    failedTemplate: String,
    onFinished: () -> Unit = {},
) {
    if (group.url.isBlank()) {
        onFinished()
        return
    }
    services.appScope.launch {
        try {
            val result = updateSubscriptions(
                groups = listOf(group),
                subscriptionFetcher = services.subscriptionFetcher,
                fetchOptions = { updateGroup -> stateStore.state.value.toSubscriptionFetchOptions(updateGroup) },
            )
            if (result.updates.isNotEmpty()) {
                applyProxySubscriptionUpdates(
                    stateStore = stateStore,
                    updates = result.updates,
                    updatedAtMillis = result.updatedAtMillis,
                    updateAppState = updateAppState,
                )
            }
            services.tipNotifier.show(
                subscriptionUpdateMessage(
                    result = result,
                    successTemplate = successTemplate,
                    failedTemplate = failedTemplate,
                ),
            )
        } finally {
            onFinished()
        }
    }
}
