// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.skipi.app.server.ProxyServerEditApplyOutcome
import app.skipi.app.server.applyProxyServerEditResult
import app.skipi.app.store.SharedApplicationStore
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
    LaunchedEffect(
        navigator,
        tipNotifier,
        sharedApplicationStore,
        messages.savedTemplate,
        messages.joinedTemplate,
        messages.deletedTemplate,
        onSelectedGroupIdChange,
    ) {
        navigator.observeResult<ProxyServerEditResult>(resultKey).collect { result ->
            navigator.clearResult(resultKey)
            when (
                val outcome = applyProxyServerEditResult(
                    result = result,
                    store = sharedApplicationStore,
                    defaultGroupId = features.subscription.DefaultSubscriptionGroupId,
                )
            ) {
                is ProxyServerEditApplyOutcome.Saved -> {
                    outcome.selectedGroupId?.let(onSelectedGroupIdChange)
                    if (outcome.wasExistingAtCommit) {
                        tipNotifier.show(messages.savedTemplate.formatTemplate("name" to result.server.getInfo().remarks))
                    } else if (result.groupId != null) {
                        tipNotifier.show(messages.joinedTemplate.formatTemplate("name" to result.server.getInfo().remarks))
                    }
                }
                ProxyServerEditApplyOutcome.Deleted -> {
                    tipNotifier.show(messages.deletedTemplate.formatTemplate("name" to result.server.getInfo().remarks))
                }
                is ProxyServerEditApplyOutcome.Failed -> {
                    tipNotifier.showError(IllegalStateException(outcome.reason))
                }
            }
        }
    }
}
