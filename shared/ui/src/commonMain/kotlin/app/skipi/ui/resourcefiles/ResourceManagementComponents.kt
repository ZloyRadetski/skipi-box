// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.resourcefiles

import app.skipi.ui.components.AppWindowDialog
import app.skipi.ui.resources.*
import app.skipi.ui.text.formatTemplate
import app.skipi.ui.text.themedFontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.resources.CustomResourceFileStatus
import features.resources.ResourceFileKind
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileSourceDraft
import features.resources.ResourceFileStatus
import features.resources.ResourceFileUpdateDisplayState
import features.resources.ResourceFileUpdateSource
import kotlin.math.round
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.Reset
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun resourceFileSourceOptions(): List<String> = listOf(
    stringResource(Res.string.settings_resource_files_source_loyalsoldier_github),
    stringResource(Res.string.settings_resource_files_source_v2fly_github),
    stringResource(Res.string.settings_resource_files_source_chocolate4u_github),
    stringResource(Res.string.settings_resource_files_source_runetfreedom_github),
    stringResource(Res.string.settings_resource_files_source_roscomvpn_github),
    stringResource(Res.string.settings_resource_files_source_custom),
)

@Composable
internal fun ResourceFileSourceCard(
    sourceOptions: List<String>,
    settings: TrafficConfigResourceSettings,
    selectedUpdateSource: ResourceFileUpdateSource,
    sourceDraft: ResourceFileSourceDraft?,
    userAgentPresets: List<String>,
    queueBusy: Boolean,
    actionsEnabled: Boolean,
    onSourceSelected: (Int) -> Unit,
    onOpenCustomSource: (ResourceFileSourceDraft) -> Unit,
    onDismissCustomSource: () -> Unit,
    onCustomSourceChange: (ResourceFileSourceDraft) -> Unit,
    onUserAgentChange: (String) -> Unit,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val geoIpUrlDraftState = rememberTextFieldState(initialText = settings.customGeoIpUrl)
    val geoSiteUrlDraftState = rememberTextFieldState(initialText = settings.customGeoSiteUrl)
    val geoIpOnlyCnPrivateUrlDraftState = rememberTextFieldState(initialText = settings.customGeoIpOnlyCnPrivateUrl)
    val directCidrIpv4UrlDraftState = rememberTextFieldState(initialText = settings.customDirectCidrIpv4Url)
    val directCidrIpv6UrlDraftState = rememberTextFieldState(initialText = settings.customDirectCidrIpv6Url)
    val selectedIndex = resourceFileSourceDisplayIndex(settings.source, sourceOptions.size)
    val sourceItems = sourceOptions.map { option -> DropdownItem(text = option) }
    val effectiveUserAgentPresets = userAgentPresets
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .ifEmpty { listOf(settings.userAgent) }
    val userAgentItems = effectiveUserAgentPresets.map { preset -> DropdownItem(text = preset) }
    val selectedUserAgentIndex = effectiveUserAgentPresets.indexOf(settings.userAgent).coerceAtLeast(0)
    val sourceAction = resourceFileSourceAction(queueBusy, actionsEnabled)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(
            color = app.skipi.ui.theme.SkipiTheme.colors.surface,
            contentColor = app.skipi.ui.theme.SkipiTheme.colors.onSurface,
        ),
    ) {
        OverlaySpinnerPreference(
            title = stringResource(Res.string.settings_resource_files_source),
            items = sourceItems,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { index ->
                when (
                    val selection = selectResourceFileSourceForUi(
                        settings = settings,
                        selectedSourceId = index,
                        resolvedSelectedSource = selectedUpdateSource,
                    )
                ) {
                    is ResourceFileSourceSelection.PersistSource -> onSourceSelected(selection.sourceId)
                    is ResourceFileSourceSelection.OpenCustomEditor -> {
                        geoIpUrlDraftState.setTextAndPlaceCursorAtEnd(selection.draft.geoIpUrl)
                        geoSiteUrlDraftState.setTextAndPlaceCursorAtEnd(selection.draft.geoSiteUrl)
                        geoIpOnlyCnPrivateUrlDraftState.setTextAndPlaceCursorAtEnd(selection.draft.geoIpOnlyCnPrivateUrl)
                        directCidrIpv4UrlDraftState.setTextAndPlaceCursorAtEnd(selection.draft.directCidrIpv4Url)
                        directCidrIpv6UrlDraftState.setTextAndPlaceCursorAtEnd(selection.draft.directCidrIpv6Url)
                        onOpenCustomSource(selection.draft)
                    }
                }
            },
        )
        CustomResourceFileSourceDialog(
            show = sourceDraft != null,
            geoIpUrlState = geoIpUrlDraftState,
            geoSiteUrlState = geoSiteUrlDraftState,
            geoIpOnlyCnPrivateUrlState = geoIpOnlyCnPrivateUrlDraftState,
            directCidrIpv4UrlState = directCidrIpv4UrlDraftState,
            directCidrIpv6UrlState = directCidrIpv6UrlDraftState,
            onDismissRequest = onDismissCustomSource,
            onSave = {
                onCustomSourceChange(
                    ResourceFileSourceDraft(
                        geoIpUrl = geoIpUrlDraftState.text.toString(),
                        geoSiteUrl = geoSiteUrlDraftState.text.toString(),
                        geoIpOnlyCnPrivateUrl = geoIpOnlyCnPrivateUrlDraftState.text.toString(),
                        directCidrIpv4Url = directCidrIpv4UrlDraftState.text.toString(),
                        directCidrIpv6Url = directCidrIpv6UrlDraftState.text.toString(),
                    ),
                )
                onSourceSelected(ResourceFileSourceCustom)
                onDismissCustomSource()
            },
        )
        OverlaySpinnerPreference(
            title = stringResource(Res.string.subscription_user_agent),
            summary = settings.userAgent,
            items = userAgentItems,
            selectedIndex = selectedUserAgentIndex,
            onSelectedIndexChange = { index ->
                onUserAgentChange(effectiveUserAgentPresets[index])
            },
        )
        ArrowPreference(
            title = stringResource(
                if (sourceAction.kind == ResourceFileSourceActionKind.Cancel) {
                    Res.string.common_cancel
                } else {
                    Res.string.settings_resource_files_update
                },
            ),
            onClick = {
                if (sourceAction.kind == ResourceFileSourceActionKind.Cancel) onCancel() else onUpdate()
            },
            enabled = sourceAction.enabled,
        )
    }
}

@Composable
private fun CustomResourceFileSourceDialog(
    show: Boolean,
    geoIpUrlState: TextFieldState,
    geoSiteUrlState: TextFieldState,
    geoIpOnlyCnPrivateUrlState: TextFieldState,
    directCidrIpv4UrlState: TextFieldState,
    directCidrIpv6UrlState: TextFieldState,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
) {
    AppWindowDialog(
        show = show,
        title = stringResource(Res.string.settings_resource_files_source_custom_title),
        onDismissRequest = onDismissRequest,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                TextField(
                    state = geoIpUrlState,
                    label = ResourceFileKind.GeoIp.fileName,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                TextField(
                    state = geoSiteUrlState,
                    label = ResourceFileKind.GeoSite.fileName,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                TextField(
                    state = geoIpOnlyCnPrivateUrlState,
                    label = ResourceFileKind.GeoIpOnlyCnPrivate.fileName,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                TextField(
                    state = directCidrIpv4UrlState,
                    label = ResourceFileKind.DirectCidrIpv4.fileName,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                TextField(
                    state = directCidrIpv6UrlState,
                    label = ResourceFileKind.DirectCidrIpv6.fileName,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        text = stringResource(Res.string.common_cancel),
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(Res.string.common_save),
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
internal fun ResourceFileCard(
    fileName: String,
    status: ResourceFileStatus,
    formatTimestamp: (Long) -> String,
    onReplace: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
    updateState: ResourceFileUpdateDisplayState = ResourceFileUpdateDisplayState.Idle,
    onUpdate: (() -> Unit)? = null,
    description: String? = null,
    actionsEnabled: Boolean = true,
) {
    val rowActions = resourceFileRowActions(
        isCustom = false,
        url = "",
        updateDisplayState = updateState,
        actionsEnabled = actionsEnabled,
    ).filter { action -> action.kind != ResourceFileRowActionKind.Update || onUpdate != null }
    ResourceFileCardSurface(
        fileName = fileName,
        status = status,
        formatTimestamp = formatTimestamp,
        description = description,
        modifier = modifier,
    ) {
        rowActions.forEach { action ->
            when (action.kind) {
                ResourceFileRowActionKind.Update -> ResourceFileUpdateAction(
                    updateState = updateState,
                    enabled = action.enabled,
                    onClick = requireNotNull(onUpdate),
                )
                ResourceFileRowActionKind.Replace -> ResourceFileIconAction(
                    kind = action.kind,
                    enabled = action.enabled,
                    onClick = onReplace,
                )
                ResourceFileRowActionKind.Restore -> ResourceFileIconAction(
                    kind = action.kind,
                    enabled = action.enabled,
                    onClick = onRestore,
                )
                ResourceFileRowActionKind.Edit,
                ResourceFileRowActionKind.Delete,
                -> Unit
            }
        }
    }
}

@Composable
private fun ResourceFileCardSurface(
    fileName: String,
    status: ResourceFileStatus,
    formatTimestamp: (Long) -> String,
    modifier: Modifier = Modifier,
    description: String? = null,
    actions: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(
            color = app.skipi.ui.theme.SkipiTheme.colors.surface,
            contentColor = app.skipi.ui.theme.SkipiTheme.colors.onSurface,
        ),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        fontSize = 18.sp,
                        fontWeight = themedFontWeight(FontWeight.SemiBold),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!description.isNullOrBlank()) {
                        Text(
                            text = description,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (status.exists) {
                        val updatedAt = if (status.updatedAtMillis > 0L) {
                            formatTimestamp(status.updatedAtMillis)
                        } else {
                            "-"
                        }
                        Text(
                            text = stringResource(Res.string.settings_resource_files_size)
                                .formatTemplate("size" to status.sizeBytes.sizeText()),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(Res.string.settings_resource_files_updated_at)
                                .formatTemplate("time" to updatedAt),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                ResourceFileStatusChip(
                    text = stringResource(
                        if (status.exists) Res.string.settings_resource_files_ready else Res.string.settings_resource_files_missing,
                    ),
                    ready = status.exists,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}

@Composable
internal fun CustomResourceFileEditorDialog(
    show: Boolean,
    nameState: TextFieldState,
    urlState: TextFieldState,
    onDismissRequest: () -> Unit,
    onSave: (name: String, url: String) -> Unit,
) {
    AppWindowDialog(
        show = show,
        title = stringResource(Res.string.settings_resource_files_custom_file),
        onDismissRequest = onDismissRequest,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                TextField(
                    state = nameState,
                    label = stringResource(Res.string.settings_resource_files_custom_name),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
                TextField(
                    state = urlState,
                    label = stringResource(Res.string.settings_resource_files_custom_url),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        text = stringResource(Res.string.common_cancel),
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(Res.string.common_save),
                        onClick = { onSave(nameState.text.toString(), urlState.text.toString()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
internal fun CustomResourceFileCard(
    fileStatus: CustomResourceFileStatus,
    formatTimestamp: (Long) -> String,
    onUpdate: (TrafficConfigCustomResourceFile) -> Unit,
    onReplace: (TrafficConfigCustomResourceFile) -> Unit,
    onEdit: (TrafficConfigCustomResourceFile) -> Unit,
    onDelete: (TrafficConfigCustomResourceFile) -> Unit,
    modifier: Modifier = Modifier,
    updateState: ResourceFileUpdateDisplayState = ResourceFileUpdateDisplayState.Idle,
    actionsEnabled: Boolean = true,
) {
    val rowActions = resourceFileRowActions(
        isCustom = true,
        url = fileStatus.file.url,
        updateDisplayState = updateState,
        actionsEnabled = actionsEnabled,
    )
    ResourceFileCardSurface(
        fileName = fileStatus.file.name,
        status = fileStatus.status,
        formatTimestamp = formatTimestamp,
        modifier = modifier,
    ) {
        rowActions.forEach { action ->
            when (action.kind) {
                ResourceFileRowActionKind.Update -> ResourceFileUpdateAction(
                    updateState = updateState,
                    enabled = action.enabled,
                    onClick = { onUpdate(fileStatus.file) },
                )
                ResourceFileRowActionKind.Replace -> ResourceFileIconAction(
                    kind = action.kind,
                    enabled = action.enabled,
                    onClick = { onReplace(fileStatus.file) },
                )
                ResourceFileRowActionKind.Edit -> ResourceFileIconAction(
                    kind = action.kind,
                    enabled = action.enabled,
                    onClick = { onEdit(fileStatus.file) },
                )
                ResourceFileRowActionKind.Delete -> ResourceFileIconAction(
                    kind = action.kind,
                    enabled = action.enabled,
                    onClick = { onDelete(fileStatus.file) },
                )
                ResourceFileRowActionKind.Restore -> Unit
            }
        }
    }
}

@Composable
private fun ResourceFileIconAction(
    kind: ResourceFileRowActionKind,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val imageVector = when (kind) {
        ResourceFileRowActionKind.Replace -> MiuixIcons.Replace
        ResourceFileRowActionKind.Restore -> MiuixIcons.Reset
        ResourceFileRowActionKind.Edit -> MiuixIcons.Edit
        ResourceFileRowActionKind.Delete -> MiuixIcons.Delete
        ResourceFileRowActionKind.Update -> MiuixIcons.Refresh
    }
    val description = when (kind) {
        ResourceFileRowActionKind.Replace -> stringResource(Res.string.common_replace)
        ResourceFileRowActionKind.Restore -> stringResource(Res.string.common_restore)
        ResourceFileRowActionKind.Edit -> stringResource(Res.string.common_edit)
        ResourceFileRowActionKind.Delete -> stringResource(Res.string.common_delete)
        ResourceFileRowActionKind.Update -> stringResource(Res.string.common_update)
    }
    IconButton(enabled = enabled, onClick = onClick) {
        Icon(
            imageVector = imageVector,
            contentDescription = description,
            tint = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
        )
    }
}

@Composable
private fun ResourceFileUpdateAction(
    updateState: ResourceFileUpdateDisplayState,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    when (updateState) {
        ResourceFileUpdateDisplayState.Idle -> IconButton(
            enabled = enabled,
            onClick = onClick,
        ) {
            Icon(
                imageVector = MiuixIcons.Refresh,
                contentDescription = stringResource(Res.string.common_update),
                tint = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.disabledOnSecondaryVariant,
            )
        }
        ResourceFileUpdateDisplayState.Queued -> IconButton(
            enabled = false,
            onClick = {},
        ) {
            Icon(
                imageVector = MiuixIcons.Refresh,
                contentDescription = stringResource(Res.string.settings_resource_file_update_queued),
                tint = MiuixTheme.colorScheme.primary,
            )
        }
        ResourceFileUpdateDisplayState.Running -> IconButton(
            enabled = false,
            onClick = {},
        ) {
            val updatingDescription = stringResource(Res.string.settings_resource_file_updating)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = updatingDescription },
                contentAlignment = Alignment.Center,
            ) {
                InfiniteProgressIndicator(
                    color = MiuixTheme.colorScheme.primary,
                    size = 20.dp,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun ResourceFileStatusChip(text: String, ready: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (ready) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MiuixTheme.colorScheme.error.copy(alpha = 0.12f),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = themedFontWeight(FontWeight.Medium),
            color = if (ready) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
        )
    }
}

private fun Long.sizeText(): String {
    if (this < 1024) return "$this B"
    val kib = this / 1024.0
    if (kib < 1024) return "${kib.formatOneDecimal()} KiB"
    val mib = kib / 1024.0
    return "${mib.formatOneDecimal()} MiB"
}

private fun Double.formatOneDecimal(): String = (round(this * 10.0) / 10.0)
    .toString()
    .removeSuffix(".0")
