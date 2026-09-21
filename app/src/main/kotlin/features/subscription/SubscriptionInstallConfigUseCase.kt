// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import android.content.Intent
import android.net.Uri
import app.AppState
import app.SubscriptionGroupState
import data.AndroidAppStateStore
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdateResult
import features.proxy.server.usecase.withUpdatedSubscriptionServers
import features.subscription.runtime.AndroidSubscriptionFetcher
import features.subscription.usecase.subscriptionUpdateMessage
import features.subscription.usecase.toSubscriptionFetchOptions
import features.subscription.usecase.updateSubscriptions
import ui.text.formatTemplate

internal data class SubscriptionInstallConfig(
    val name: String,
    val url: String,
    val userAgent: String,
)

internal data class SubscriptionInstallResult(
    val updateResult: ProxyServerListSubscriptionUpdateResult,
    val existingGroupName: String?,
)

internal class SubscriptionInstallConfigUseCase(
    private val stateStore: AndroidAppStateStore,
    private val subscriptionFetcher: AndroidSubscriptionFetcher,
) {
    suspend fun install(config: SubscriptionInstallConfig): SubscriptionInstallResult {
        val preparedGroup = stateStore.prepareSubscriptionInstallGroup(config)
        val result = updateSubscriptions(
            groups = listOf(preparedGroup.group),
            subscriptionFetcher = subscriptionFetcher,
            fetchOptions = { stateStore.state.value.toSubscriptionFetchOptions(it) },
        )
        if (result.updates.isNotEmpty()) {
            stateStore.update { state ->
                state.withUpdatedSubscriptionServers(
                    updates = result.updates,
                    updatedAtMillis = result.updatedAtMillis,
                )
            }
        }
        return SubscriptionInstallResult(
            updateResult = result,
            existingGroupName = preparedGroup.existingGroupName,
        )
    }
}

internal fun subscriptionInstallMessage(
    result: SubscriptionInstallResult,
    existingUrlTemplate: String,
    successTemplate: String,
    failedTemplate: String,
): String {
    val updateMessage = subscriptionUpdateMessage(
        result = result.updateResult,
        successTemplate = successTemplate,
        failedTemplate = failedTemplate,
    )
    val existingGroupName = result.existingGroupName ?: return updateMessage
    return listOf(
        existingUrlTemplate.formatTemplate("name" to existingGroupName),
        updateMessage,
    ).joinToString(separator = "\n")
}

internal fun Intent.toSubscriptionInstallConfigOrNull(): SubscriptionInstallConfig? {
    if (action != Intent.ACTION_VIEW) return null
    return data?.toString()?.toSubscriptionInstallConfigOrNull()
}

internal fun String.toSubscriptionInstallConfigOrNull(): SubscriptionInstallConfig? {
    return parseSubscriptionInstallUriOrNull(
        value = this,
        policy = AndroidSubscriptionInstallUriParsingPolicy,
    )?.toSubscriptionInstallConfig()
}

internal fun Uri.isSubscriptionInstallConfigUri(): Boolean {
    return toString().isSubscriptionInstallUri()
}

private fun SubscriptionInstallUri.toSubscriptionInstallConfig(): SubscriptionInstallConfig {
    return SubscriptionInstallConfig(
        name = name,
        url = url,
        userAgent = source.androidUserAgent,
    )
}
private val AndroidSubscriptionInstallUriParsingPolicy = SubscriptionInstallUriParsingPolicy(
    directUrlPolicy = SubscriptionInstallUrlPolicy.HttpsOnly,
    embeddedUrlPolicy = SubscriptionInstallUrlPolicy.HttpsOnly,
)

private val SubscriptionInstallSource.androidUserAgent: String
    get() = when (this) {
        SubscriptionInstallSource.RawHttp,
        SubscriptionInstallSource.V2rayNg -> DefaultSubscriptionUserAgent

        SubscriptionInstallSource.Clash,
        SubscriptionInstallSource.ClashMeta -> ClashMetaSubscriptionUserAgent

        SubscriptionInstallSource.FlClashX -> FlClashXSubscriptionUserAgent
    }

private fun AndroidAppStateStore.prepareSubscriptionInstallGroup(
    config: SubscriptionInstallConfig,
): PreparedSubscriptionInstallGroup {
    var preparedGroup: PreparedSubscriptionInstallGroup? = null
    update { state ->
        val existingGroup = state.existingSubscriptionGroupByUrl(config.url)
        if (existingGroup != null) {
            preparedGroup = PreparedSubscriptionInstallGroup(
                group = existingGroup,
                existingGroupName = existingGroup.name,
            )
            return@update state
        }
        // Group 1 is the permanent virtual "Manual servers" group.  A real
        // subscription must never reuse it: otherwise an update could delete
        // direct links that the user added by hand.
        val group = state.newSubscriptionGroup(config)
        preparedGroup = PreparedSubscriptionInstallGroup(
            group = group,
            existingGroupName = null,
        )
        state.copy(
            subscriptionGroups = state.subscriptionGroups + group,
            nextSubscriptionGroupId = state.nextSubscriptionGroupId + 1,
        )
    }
    return checkNotNull(preparedGroup)
}

private data class PreparedSubscriptionInstallGroup(
    val group: SubscriptionGroupState,
    val existingGroupName: String?,
)

private fun AppState.existingSubscriptionGroupByUrl(url: String): SubscriptionGroupState? {
    return subscriptionGroups.firstOrNull { group -> group.url == url }
}

private fun AppState.newSubscriptionGroup(config: SubscriptionInstallConfig): SubscriptionGroupState {
    return SubscriptionGroupState(
        id = nextSubscriptionGroupId,
        name = config.name,
        url = config.url,
        userAgent = config.userAgent,
        updateInterval = "",
        updateViaProxy = false,
        enabled = true,
    )
}
