// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.LocalAppStateStore
import app.R
import app.SubscriptionGroupState
import app.collectAppState
import app.skipi.ui.subscription.SubscriptionGroupCard as SharedSubscriptionGroupCard
import app.skipi.ui.subscription.SubscriptionGroupEditorDialog as SharedSubscriptionGroupEditorDialog
import app.skipi.ui.subscription.SubscriptionGroupUiState
import features.proxy.server.display.displayName

@Composable
internal fun SubscriptionGroupEditorDialog(
    show: Boolean,
    group: SubscriptionGroupState?,
    nextGroupId: Int,
    isManualGroup: Boolean = false,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit,
    onSave: (SubscriptionGroupState, isNew: Boolean) -> Unit,
    onDelete: ((SubscriptionGroupState) -> Unit)? = null,
    onInvalidUrl: () -> Unit,
) {
    val state by LocalAppStateStore.current.collectAppState()
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    SharedSubscriptionGroupEditorDialog(
        show = show,
        group = group?.toSubscriptionGroupUiState(defaultGroupName),
        nextGroupId = nextGroupId,
        userAgentOptions = state.subscriptionUserAgents,
        defaultUserAgent = DefaultSubscriptionUserAgent,
        defaultExpiryReminders = state.subscriptionExpiryReminders,
        confirmDeletion = state.enableDeletionConfirmation,
        isManualGroup = isManualGroup,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        onSave = { edited, isNew -> onSave(edited.toSubscriptionGroupState(group), isNew) },
        onDelete = onDelete?.let { callback -> { edited -> callback(edited.toSubscriptionGroupState(group)) } },
        onInvalidUrl = onInvalidUrl,
    )
}

@Composable
internal fun SubscriptionGroupCard(
    group: SubscriptionGroupState,
    onToggle: (Boolean) -> Unit,
    onUpdate: (() -> Unit)?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isUpdating: Boolean = false,
) {
    val defaultGroupName = stringResource(R.string.subscription_default_group)
    SharedSubscriptionGroupCard(
        group = group.toSubscriptionGroupUiState(defaultGroupName),
        onToggle = onToggle,
        onUpdate = onUpdate,
        onEdit = onEdit,
        onDelete = onDelete,
        modifier = modifier,
        isUpdating = isUpdating,
    )
}

internal fun SubscriptionGroupState.toSubscriptionGroupUiState(defaultGroupName: String): SubscriptionGroupUiState =
    SubscriptionGroupUiState(
        id = id,
        name = displayName(defaultGroupName),
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        hwid = hwid,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = autoOverrideRules,
        enabled = enabled,
        builtIn = builtIn,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        profileTitle = profileTitle,
        announce = announce,
        supportUrl = supportUrl,
        supportEmail = supportEmail,
        profileWebPageUrl = profileWebPageUrl,
        announceUrl = announceUrl,
        trafficUploadBytes = trafficUploadBytes,
        trafficDownloadBytes = trafficDownloadBytes,
        trafficTotalBytes = trafficTotalBytes,
        trafficExpireAtSeconds = trafficExpireAtSeconds,
        notifyOnExpiry = notifyOnExpiry,
        customExpiryReminders = customExpiryReminders,
    )

internal fun SubscriptionGroupUiState.toSubscriptionGroupState(original: SubscriptionGroupState?): SubscriptionGroupState =
    original?.copy(
        name = if (original.builtIn) original.name else name,
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        hwid = hwid,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = autoOverrideRules,
        enabled = enabled,
        notifyOnExpiry = notifyOnExpiry,
        customExpiryReminders = customExpiryReminders,
    ) ?: SubscriptionGroupState(
        id = id,
        name = name,
        url = url,
        userAgent = userAgent,
        updateInterval = updateInterval,
        hwid = hwid,
        ageSecretKey = ageSecretKey,
        updateViaProxy = updateViaProxy,
        autoOverrideRules = autoOverrideRules,
        enabled = enabled,
        builtIn = builtIn,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        profileTitle = profileTitle,
        announce = announce,
        supportUrl = supportUrl,
        supportEmail = supportEmail,
        profileWebPageUrl = profileWebPageUrl,
        announceUrl = announceUrl,
        trafficUploadBytes = trafficUploadBytes,
        trafficDownloadBytes = trafficDownloadBytes,
        trafficTotalBytes = trafficTotalBytes,
        trafficExpireAtSeconds = trafficExpireAtSeconds,
        notifyOnExpiry = notifyOnExpiry,
        customExpiryReminders = customExpiryReminders,
    )
