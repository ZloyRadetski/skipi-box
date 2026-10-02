// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationStore
import features.subscription.DefaultSubscriptionGroupId
import app.navigation.Navigator
import app.navigation.ProxyServerEditResult
import ui.feedback.AndroidToastTipNotifier
import ui.text.formatTemplate

@Composable
internal fun ProxyServerEditResultHandler(
    navigator: Navigator,
    resultKey: String,
    messages: ProxyServerListMessages,
    sharedApplicationStore: SharedApplicationStore,
    tipNotifier: AndroidToastTipNotifier,
    onSelectedGroupIdChange: (Int) -> Unit,
) {
    LaunchedEffect(navigator, tipNotifier, messages.savedTemplate, messages.joinedTemplate) {
        navigator.observeResult<ProxyServerEditResult>(resultKey).collect { result ->
            navigator.clearResult(resultKey)
            val existing = sharedApplicationStore.state.value.proxyServers.firstOrNull { it.id == result.serverId }
            val wasExisting = existing != null
            val existingGroupId = existing?.sourceSubscriptionId
            sharedApplicationStore.dispatch(
                SharedApplicationAction.UpsertProxyServer(
                    ProxyServerRecord(
                        id = result.serverId,
                        server = result.server,
                        sourceSubscriptionId = result.groupId,
                    ),
                ),
            )
            if (wasExisting) {
                onSelectedGroupIdChange(result.returnGroupId ?: existingGroupId ?: DefaultSubscriptionGroupId)
                tipNotifier.show(messages.savedTemplate.formatTemplate("name" to result.server.getInfo().remarks))
            } else if (result.groupId != null) {
                onSelectedGroupIdChange(result.returnGroupId ?: result.groupId)
                tipNotifier.show(messages.joinedTemplate.formatTemplate("name" to result.server.getInfo().remarks))
            }
        }
    }
}
