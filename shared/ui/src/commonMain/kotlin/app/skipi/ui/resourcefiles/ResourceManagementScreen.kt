// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi::class)

package app.skipi.ui.resourcefiles

import androidx.compose.ui.graphics.Color
import app.skipi.ui.components.BackNavigationIcon
import app.skipi.ui.components.DeleteConfirmationDialog
import app.skipi.ui.components.NavigationIcon
import app.skipi.ui.layout.AdaptiveTopAppBar
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageListPadding
import app.skipi.ui.layout.pageScrollModifiers
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_geo_auto_update
import app.skipi.ui.resources.configs_geo_auto_update_interval
import app.skipi.ui.resources.configs_geo_auto_update_summary
import app.skipi.ui.resources.configs_resources
import app.skipi.ui.resources.deletion_confirmation_delete_resource_file
import app.skipi.ui.resources.settings_resource_file_updated
import app.skipi.ui.resources.settings_resource_files_add_custom
import app.skipi.ui.resources.settings_resource_files_custom_name_duplicate
import app.skipi.ui.resources.settings_resource_files_custom_name_invalid
import app.skipi.ui.resources.settings_resource_files_deleted
import app.skipi.ui.resources.settings_resource_files_files
import app.skipi.ui.resources.settings_resource_files_replaced
import app.skipi.ui.resources.settings_resource_files_restored
import app.skipi.ui.resources.settings_resource_files_updated
import app.skipi.ui.text.formatTemplate
import app.skipi.ui.theme.SkipiTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.then
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.resources.CustomResourceFileStatus
import features.resources.ResourceFileEditorEffect
import features.resources.ResourceFileEditorResult
import features.resources.ResourceFileKind
import features.resources.ResourceFileNameValidation
import features.resources.ResourceFileOperations
import features.resources.ResourceFileScope
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileStatus
import features.resources.ResourceFileUpdateOptions
import features.resources.ResourceFileUpdateQueue
import features.resources.ResourceFileUpdateRequest
import features.resources.ResourceFileUpdateResult
import features.resources.ResourceFileUpdateTarget
import features.resources.ResourceFilesStatus
import features.resources.addCustomResourceFile
import features.resources.applyCustomResourceFileSourceDraft
import features.resources.deleteCustomResourceFile
import features.resources.editCustomResourceFile
import features.resources.isBusy
import features.resources.displayStateOf
import features.resources.resourceFileUpdateSource
import features.resources.selectResourceFileSource
import features.resources.ResourceFileSourceDraft
import features.subscription.sanitizeSubscriptionIntervalInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** Resource management UI shared by Android and Desktop; hosts supply local file operations. */
@Composable
fun ResourceManagementScreen(
    profile: TrafficConfigState,
    padding: PaddingValues,
    isWideScreen: Boolean,
    queue: ResourceFileUpdateQueue,
    operations: ResourceFileOperations,
    updateOptionsForUserAgent: (String) -> ResourceFileUpdateOptions,
    userAgentPresets: List<String>,
    deletionConfirmationEnabled: Boolean,
    actionScope: CoroutineScope,
    onBack: () -> Unit,
    updateSettings: ((TrafficConfigResourceSettings) -> TrafficConfigResourceSettings) -> Unit,
    formatTimestamp: (Long) -> String,
    onTip: suspend (String) -> Unit,
    onError: suspend (Throwable) -> Unit,
) {
    val resourceSettings = profile.resourceSettings
    val resourceScope = ResourceFileScope(profile.id, resourceSettings.source)
    val queueState by queue.state.collectAsState()
    val sourceOptions = resourceFileSourceOptions()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var status by remember { mutableStateOf(ResourceFilesStatus()) }
    var resourceActionRunning by remember { mutableStateOf(false) }
    var dialogs by remember { mutableStateOf(ResourceFileManagementDialogState()) }
    val addNameState = rememberTextFieldState()
    val addUrlState = rememberTextFieldState()
    val editNameState = rememberTextFieldState()
    val editUrlState = rememberTextFieldState()
    val updatedMessage = stringResource(Res.string.settings_resource_files_updated)
    val updatedOneMessage = stringResource(Res.string.settings_resource_file_updated)
    val replacedMessage = stringResource(Res.string.settings_resource_files_replaced)
    val restoredMessage = stringResource(Res.string.settings_resource_files_restored)
    val deletedMessage = stringResource(Res.string.settings_resource_files_deleted)
    val invalidNameMessage = stringResource(Res.string.settings_resource_files_custom_name_invalid)
    val duplicateNameMessage = stringResource(Res.string.settings_resource_files_custom_name_duplicate)

    fun runResourceFileAction(
        action: suspend () -> ResourceFilesStatus?,
        successMessage: String?,
    ) {
        if (resourceActionRunning) return
        resourceActionRunning = true
        actionScope.launch {
            try {
                action()?.let { nextStatus ->
                    withContext(Dispatchers.Main.immediate) {
                        status = nextStatus
                    }
                    successMessage?.let { message -> onTip(message) }
                }
            } catch (error: Throwable) {
                onError(error)
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    resourceActionRunning = false
                }
            }
        }
    }

    fun showEditorError(message: String) {
        actionScope.launch { onTip(message) }
    }

    fun runSettingsAction(action: () -> Unit) {
        runResourceFileSettingsAction(action) { error ->
            actionScope.launch { onError(error) }
        }
    }

    suspend fun performEditorEffect(effect: ResourceFileEditorEffect): ResourceFilesStatus? = when (effect) {
        is ResourceFileEditorEffect.ReplaceCustom -> operations.replaceCustom(
            scope = resourceScope,
            customFile = effect.file,
            customResourceFiles = effect.customFiles,
        )
        is ResourceFileEditorEffect.RenameCustom -> operations.renameCustom(
            scope = resourceScope,
            previousFile = effect.previousFile,
            customFile = effect.file,
            customResourceFiles = effect.customFiles,
        )
        is ResourceFileEditorEffect.DeleteCustom -> operations.deleteCustom(
            scope = resourceScope,
            customFile = effect.file,
            customResourceFiles = effect.customFiles,
        )
    }

    fun runEditorEffect(effect: ResourceFileEditorEffect, successMessage: String?) {
        runResourceFileAction(
            action = { performEditorEffect(effect) },
            successMessage = successMessage,
        )
    }

    fun updateSource(sourceId: Int) {
        runSettingsAction {
            updateSettings { settings -> selectResourceFileSource(settings, sourceId.coerceIn(sourceOptions.indices)) }
        }
    }

    fun saveCustomSource(draft: ResourceFileSourceDraft) {
        val selectedSource = resourceSettings.resourceFileUpdateSource()
        runSettingsAction {
            updateSettings { settings ->
                applyCustomResourceFileSourceDraft(settings, draft, selectedSource)
            }
            updateSettings { settings -> selectResourceFileSource(settings, ResourceFileSourceCustom) }
            dialogs = dialogs.copy(sourceDraft = null)
        }
    }

    fun updateCustomFileFromEditor(name: String, url: String, editingFile: TrafficConfigCustomResourceFile?) {
        runSettingsAction {
            val result = if (editingFile == null) {
                addCustomResourceFile(
                    displayedSettings = resourceSettings,
                    name = name,
                    url = url,
                    updateSettings = updateSettings,
                    afterCommit = { effect ->
                        runEditorEffect(
                            effect,
                            successMessage = (effect as? ResourceFileEditorEffect.ReplaceCustom)?.let {
                                replacedMessage.formatTemplate("name" to it.file.name)
                            },
                        )
                    },
                )
            } else {
                editCustomResourceFile(
                    displayedSettings = resourceSettings,
                    previousFile = editingFile,
                    name = name,
                    url = url,
                    updateSettings = updateSettings,
                    afterCommit = { effect -> runEditorEffect(effect, successMessage = null) },
                )
            }

            if (result is ResourceFileEditorResult.Rejected) {
                showEditorError(
                    when (result.reason) {
                        ResourceFileNameValidation.Invalid -> invalidNameMessage
                        ResourceFileNameValidation.Duplicate -> duplicateNameMessage
                        is ResourceFileNameValidation.Valid -> error("A valid name cannot be rejected")
                    },
                )
            }
            dialogs = acceptResourceFileEditorResult(
                state = dialogs,
                result = result,
                editing = editingFile != null,
            )
        }
    }

    fun deleteCustomFile(file: TrafficConfigCustomResourceFile) {
        runResourceFileAction(
            action = {
                var effect: ResourceFileEditorEffect.DeleteCustom? = null
                deleteCustomResourceFile(file, updateSettings) { committedEffect ->
                    effect = committedEffect as ResourceFileEditorEffect.DeleteCustom
                }
                performEditorEffect(requireNotNull(effect))
            },
            successMessage = deletedMessage.formatTemplate("name" to file.name),
        )
    }

    fun requestCustomFileDeletion(file: TrafficConfigCustomResourceFile) {
        val request = requestResourceFileDeletion(dialogs, file, deletionConfirmationEnabled)
        dialogs = request.state
        request.deleteImmediately?.let(::deleteCustomFile)
    }

    LaunchedEffect(resourceSettings.customFiles, queueState.completionRevision) {
        status = operations.status(resourceScope, resourceSettings.customFiles)
    }
    LaunchedEffect(queue, updatedMessage, updatedOneMessage) {
        queue.results.collect { result ->
            when (result) {
                is ResourceFileUpdateResult.Success -> {
                    val message = when (val request = result.request) {
                        is ResourceFileUpdateRequest.All -> updatedMessage
                        is ResourceFileUpdateRequest.BuiltIn -> updatedOneMessage.formatTemplate(
                            "name" to request.kind.displayName,
                        )
                        is ResourceFileUpdateRequest.Custom -> updatedOneMessage.formatTemplate(
                            "name" to request.file.name,
                        )
                    }
                    onTip(message)
                }
                is ResourceFileUpdateResult.Failure -> onError(result.error)
                is ResourceFileUpdateResult.Cancelled -> Unit
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(Res.string.configs_resources),
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    NavigationIcon(
                        onClick = {
                            addNameState.clearText()
                            addUrlState.clearText()
                            dialogs = dialogs.copy(showAddEditor = true)
                        },
                        imageVector = MiuixIcons.Add,
                        contentDescription = stringResource(Res.string.settings_resource_files_add_custom),
                    )
                },
            )
        },
    ) { innerPadding ->
        val lazyListState = rememberLazyListState()
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val listPadding = pageListPadding(contentPadding)

        Box(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = listPadding,
            ) {
                item(key = "resource_files_title") {
                    SmallTitle(text = stringResource(Res.string.settings_resource_files_files))
                }
                item(key = "resource_files_source") {
                    ResourceFileSourceCard(
                        sourceOptions = sourceOptions,
                        settings = resourceSettings,
                        selectedUpdateSource = resourceSettings.resourceFileUpdateSource(),
                        sourceDraft = dialogs.sourceDraft,
                        userAgentPresets = userAgentPresets,
                        queueBusy = queueState.isBusy,
                        actionsEnabled = !resourceActionRunning,
                        onSourceSelected = ::updateSource,
                        onOpenCustomSource = { draft -> dialogs = dialogs.copy(sourceDraft = draft) },
                        onDismissCustomSource = { dialogs = dialogs.copy(sourceDraft = null) },
                        onSaveCustomSource = ::saveCustomSource,
                        onUserAgentChange = { userAgent ->
                            runSettingsAction {
                                updateSettings { settings -> settings.copy(userAgent = userAgent) }
                            }
                        },
                        onUpdate = {
                            queue.enqueue(
                                ResourceFileUpdateRequest.All(
                                    scope = resourceScope,
                                    source = resourceSettings.resourceFileUpdateSource(),
                                    options = updateOptionsForUserAgent(resourceSettings.userAgent),
                                    customResourceFiles = resourceSettings.customFiles,
                                ),
                            )
                        },
                        onCancel = queue::cancelAll,
                    )
                }
                item(key = "geo_auto_update") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp),
                        colors = CardDefaults.defaultColors(
                            color = SkipiTheme.colors.surface,
                            contentColor = SkipiTheme.colors.onSurface,
                        ),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = stringResource(Res.string.configs_geo_auto_update),
                                summary = stringResource(Res.string.configs_geo_auto_update_summary),
                                checked = resourceSettings.autoUpdate,
                                onCheckedChange = { checked ->
                                    runSettingsAction {
                                        updateSettings { settings -> settings.copy(autoUpdate = checked) }
                                    }
                                },
                            )
                            if (resourceSettings.autoUpdate) {
                                TextField(
                                    state = rememberTextFieldState(initialText = resourceSettings.updateInterval),
                                    inputTransformation = InputTransformation
                                        .byValue { _, proposed -> sanitizeSubscriptionIntervalInput(proposed.toString()) }
                                        .then {
                                            val nextInterval = asCharSequence().toString()
                                            runSettingsAction {
                                                updateSettings { settings -> settings.copy(updateInterval = nextInterval) }
                                            }
                                        },
                                    label = stringResource(Res.string.configs_geo_auto_update_interval),
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                )
                            }
                        }
                    }
                }
                listOf(
                    ResourceFileKind.GeoIp,
                    ResourceFileKind.GeoSite,
                    ResourceFileKind.GeoIpOnlyCnPrivate,
                    ResourceFileKind.DirectCidrIpv4,
                    ResourceFileKind.DirectCidrIpv6,
                ).forEach { kind ->
                    item(key = kind.fileName) {
                        ResourceFileCard(
                            fileName = kind.displayName,
                            status = status.statusOf(kind),
                            formatTimestamp = formatTimestamp,
                            updateState = queueState.displayStateOf(ResourceFileUpdateTarget.BuiltIn(kind)),
                            actionsEnabled = !resourceActionRunning,
                            onUpdate = {
                                queue.enqueue(
                                    ResourceFileUpdateRequest.BuiltIn(
                                        scope = resourceScope,
                                        kind = kind,
                                        source = resourceSettings.resourceFileUpdateSource(),
                                        options = updateOptionsForUserAgent(resourceSettings.userAgent),
                                        customResourceFiles = resourceSettings.customFiles,
                                    ),
                                )
                            },
                            onReplace = {
                                runResourceFileAction(
                                    action = { operations.replace(resourceScope, kind, resourceSettings.customFiles) },
                                    successMessage = replacedMessage.formatTemplate("name" to kind.displayName),
                                )
                            },
                            onRestore = {
                                runResourceFileAction(
                                    action = {
                                        operations.restoreBundled(resourceScope, kind, resourceSettings.customFiles)
                                    },
                                    successMessage = restoredMessage.formatTemplate("name" to kind.displayName),
                                )
                            },
                        )
                    }
                }
                resourceSettings.customFiles.forEach { customFile ->
                    item(key = "custom_resource_file_${customFile.id}") {
                        CustomResourceFileCard(
                            fileStatus = status.statusOf(customFile),
                            formatTimestamp = formatTimestamp,
                            updateState = queueState.displayStateOf(ResourceFileUpdateTarget.Custom(customFile.id)),
                            actionsEnabled = !resourceActionRunning,
                            onUpdate = { file ->
                                queue.enqueue(
                                    ResourceFileUpdateRequest.Custom(
                                        scope = resourceScope,
                                        file = file,
                                        options = updateOptionsForUserAgent(resourceSettings.userAgent),
                                        customResourceFiles = resourceSettings.customFiles,
                                    ),
                                )
                            },
                            onReplace = { file ->
                                runResourceFileAction(
                                    action = {
                                        operations.replaceCustom(resourceScope, file, resourceSettings.customFiles)
                                    },
                                    successMessage = replacedMessage.formatTemplate("name" to file.name),
                                )
                            },
                            onEdit = { file ->
                                editNameState.setTextAndPlaceCursorAtEnd(file.name)
                                editUrlState.setTextAndPlaceCursorAtEnd(file.url)
                                dialogs = dialogs.copy(editingFile = file)
                            },
                            onDelete = ::requestCustomFileDeletion,
                        )
                    }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }

        CustomResourceFileEditorDialog(
            show = dialogs.showAddEditor,
            nameState = addNameState,
            urlState = addUrlState,
            onDismissRequest = { dialogs = dialogs.copy(showAddEditor = false) },
            onSave = { name, url -> updateCustomFileFromEditor(name, url, editingFile = null) },
        )
        CustomResourceFileEditorDialog(
            show = dialogs.editingFile != null,
            nameState = editNameState,
            urlState = editUrlState,
            onDismissRequest = { dialogs = dialogs.copy(editingFile = null) },
            onSave = { name, url ->
                dialogs.editingFile?.let { editingFile ->
                    updateCustomFileFromEditor(name, url, editingFile)
                }
            },
        )
        dialogs.pendingDeletion?.let { file ->
            DeleteConfirmationDialog(
                show = true,
                title = stringResource(Res.string.deletion_confirmation_delete_resource_file),
                onDismissRequest = { dialogs = dismissResourceFileDeletion(dialogs) },
                onConfirm = {
                    val confirmation = confirmResourceFileDeletion(dialogs)
                    dialogs = confirmation.state
                    confirmation.fileToDelete?.let(::deleteCustomFile)
                },
            )
        }
    }
}

private fun ResourceFilesStatus.statusOf(kind: ResourceFileKind): ResourceFileStatus = when (kind) {
    ResourceFileKind.GeoIp -> geoIp
    ResourceFileKind.GeoSite -> geoSite
    ResourceFileKind.GeoIpOnlyCnPrivate -> geoIpOnlyCnPrivate
    ResourceFileKind.DirectCidrIpv4 -> directCidrIpv4
    ResourceFileKind.DirectCidrIpv6 -> directCidrIpv6
    ResourceFileKind.XrayCore -> xrayCore
}

private fun ResourceFilesStatus.statusOf(file: TrafficConfigCustomResourceFile): CustomResourceFileStatus =
    customResourceFiles.firstOrNull { current -> current.file.id == file.id }
        ?: CustomResourceFileStatus(file = file)
