// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import android.content.Intent
import android.net.Uri
import features.proxy.server.usecase.applyProxySubscriptionUpdates
import app.SubscriptionGroupState
import app.skipi.app.subscription.SubscriptionInstallOperations
import app.skipi.app.subscription.SubscriptionInstallPreparation
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
            applyProxySubscriptionUpdates(
                stateStore = stateStore,
                updates = result.updates,
                updatedAtMillis = result.updatedAtMillis,
                updateAppState = { transform -> stateStore.update(transform) },
            )
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

private suspend fun AndroidAppStateStore.prepareSubscriptionInstallGroup(
    config: SubscriptionInstallConfig,
): PreparedSubscriptionInstallGroup {
    var preparation: SubscriptionInstallPreparation? = null
    appRepositories.subscriptions.updateCatalog { catalog ->
        SubscriptionInstallOperations.prepare(
            catalog = catalog,
            title = config.name,
            url = config.url,
            userAgent = config.userAgent,
        ).also { result -> preparation = result }.catalog
    }
    val prepared = checkNotNull(preparation)
    val group = currentState.subscriptionGroups.firstOrNull { it.id == prepared.subscription.id }
        ?: error("Prepared Android subscription group is missing")
    return PreparedSubscriptionInstallGroup(
        group = group,
        existingGroupName = prepared.existingTitle,
    )
}

private data class PreparedSubscriptionInstallGroup(
    val group: SubscriptionGroupState,
    val existingGroupName: String?,
)
