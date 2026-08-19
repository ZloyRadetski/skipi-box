// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import ui.AppTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.AppState
import app.CustomResourceFileState
import app.CustomResourceFileStatus
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.ResourceFileKind
import app.ResourceFileUpdateSource
import app.ResourceFileUpdateSources
import app.ResourceFilesStatus
import app.collectAppState
import app.customResourceFileNameOrNull
import app.statusOf
import engine.network.toPortOrNull
import features.config.TrafficConfigResourceSettings
import features.config.withSkipiSettingsInRawConfig
import features.config.withUpdatedTrafficConfig
import features.subscription.sanitizeSubscriptionIntervalInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.SwitchPreference
import ui.components.BackNavigationIcon
import ui.components.DeleteConfirmationDialog
import ui.components.NavigationIcon
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers
import ui.text.formatTemplate

@Composable
fun ResourceManagementPage(
    padding: PaddingValues,
    trafficConfigId: Int,
) {
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val trafficConfig = appState.trafficConfigs.firstOrNull { config -> config.id == trafficConfigId }
        ?: return
    val resourceSettings = trafficConfig.resourceSettings
    val services = LocalAppServices.current
    val resourceFileUseCase = services.resourceFileUseCase
    val resourceFileUpdateCoordinator = services.resourceFileUpdateCoordinator
    val updateQueueState by resourceFileUpdateCoordinator.state.collectAsState()
    val sourceOptions = settingsResourceFileSourceOptions()
    val tipNotifier = services.tipNotifier
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    var status by remember { mutableStateOf(ResourceFilesStatus()) }
    var resourceActionRunning by remember { mutableStateOf(false) }
    val showCustomResourceFileDialog = remember { mutableStateOf(false) }
    var editingCustomResourceFile by remember { mutableStateOf<CustomResourceFileState?>(null) }
    var pendingCustomResourceFileDeletion by remember { mutableStateOf<CustomResourceFileState?>(null) }
    val customResourceFileNameState = rememberTextFieldState()
    val customResourceFileUrlState = rememberTextFieldState()
    val editCustomResourceFileNameState = rememberTextFieldState()
    val editCustomResourceFileUrlState = rememberTextFieldState()
    val updatedMessage = stringResource(R.string.settings_resource_files_updated)
    val updatedOneMessage = stringResource(R.string.settings_resource_file_updated)
    val replacedMessage = stringResource(R.string.settings_resource_files_replaced)
    val restoredMessage = stringResource(R.string.settings_resource_files_restored)
    val deletedMessage = stringResource(R.string.settings_resource_files_deleted)
    val customResourceFileNameInvalidMessage = stringResource(
        R.string.settings_resource_files_custom_name_invalid,
    )
    val customResourceFileNameDuplicateMessage = stringResource(
        R.string.settings_resource_files_custom_name_duplicate,
    )

    fun runResourceFileAction(
        action: suspend () -> ResourceFilesStatus?,
        successMessage: String?,
    ) {
        if (resourceActionRunning) return
        resourceActionRunning = true
        services.appScope.launch {
            try {
                action()?.let {
                    withContext(Dispatchers.Main.immediate) {
                        status = it
                    }
                    successMessage?.let { message -> tipNotifier.show(message) }
                }
            } catch (error: Throwable) {
                tipNotifier.showError(error)
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    resourceActionRunning = false
                }
            }
        }
    }

    fun showResourceFileEditorError(message: String) {
        services.appScope.launch {
            tipNotifier.show(message)
        }
    }

    fun updateResourceSettings(transform: (TrafficConfigResourceSettings) -> TrafficConfigResourceSettings) {
        updateAppState { state ->
            state.withUpdatedTrafficConfig(trafficConfigId) { config ->
                config.copy(resourceSettings = transform(config.resourceSettings)).withSkipiSettingsInRawConfig()
            }
        }
    }

    fun updateResourceFile(kind: ResourceFileKind) {
        resourceFileUpdateCoordinator.enqueue(
            ResourceFileUpdateRequest.BuiltIn(
                kind = kind,
                source = resourceSettings.resourceFileUpdateSource(),
                options = appState.resourceFileUpdateOptions(resourceSettings.userAgent),
                customResourceFiles = resourceSettings.customFiles,
            ),
        )
    }

    fun updateCustomResourceFile(file: CustomResourceFileState) {
        resourceFileUpdateCoordinator.enqueue(
            ResourceFileUpdateRequest.Custom(
                file = file,
                options = appState.resourceFileUpdateOptions(resourceSettings.userAgent),
                customResourceFiles = resourceSettings.customFiles,
            ),
        )
    }

    fun customResourceFileReservedNames(editingFileId: Int? = null): Set<String> {
        return ResourceFileKind.entries.map { kind -> kind.fileName }.toSet() +
            resourceSettings.customFiles
                .filterNot { file -> file.id == editingFileId }
                .map { file -> file.name }
    }

    fun validatedCustomResourceFileName(name: String, reservedNames: Set<String>): String? {
        val fileName = customResourceFileNameOrNull(name)
        if (fileName == null) {
            showResourceFileEditorError(customResourceFileNameInvalidMessage)
            return null
        }
        if (fileName in reservedNames) {
            showResourceFileEditorError(customResourceFileNameDuplicateMessage)
            return null
        }
        return fileName
    }

    fun addCustomResourceFile(name: String, url: String): Boolean {
        val fileName = validatedCustomResourceFileName(
            name = name,
            reservedNames = customResourceFileReservedNames(),
        ) ?: return false
        var addedFile: CustomResourceFileState? = null
        var nextCustomResourceFiles = resourceSettings.customFiles
        updateResourceSettings { settings ->
            val updateUrl = url.trim()
            val fileId = settings.nextCustomFileId
            val nextCustomFile = CustomResourceFileState(
                id = fileId,
                name = fileName,
                url = updateUrl,
            )
            addedFile = nextCustomFile
            nextCustomResourceFiles = settings.customFiles + nextCustomFile
            settings.copy(
                customFiles = nextCustomResourceFiles,
                nextCustomFileId = fileId + 1,
            )
        }
        addedFile?.takeIf { file -> file.url.isBlank() }?.let { file ->
            runResourceFileAction(
                action = {
                    resourceFileUseCase.replaceCustom(
                        customFile = file,
                        customResourceFiles = nextCustomResourceFiles,
                    )
                },
                successMessage = replacedMessage.formatTemplate("name" to file.name),
            )
        }
        return true
    }

    fun editCustomResourceFile(file: CustomResourceFileState, name: String, url: String): Boolean {
        val fileName = validatedCustomResourceFileName(
            name = name,
            reservedNames = customResourceFileReservedNames(editingFileId = file.id),
        ) ?: return false
        var editedFile: CustomResourceFileState? = null
        var nextCustomResourceFiles = resourceSettings.customFiles
        updateResourceSettings { settings ->
            val updateUrl = url.trim()
            val nextCustomFile = file.copy(
                name = fileName,
                url = updateUrl,
            )
            editedFile = nextCustomFile
            nextCustomResourceFiles = settings.customFiles.map { customFile ->
                if (customFile.id == file.id) nextCustomFile else customFile
            }
            settings.copy(customFiles = nextCustomResourceFiles)
        }
        editedFile?.let { nextFile ->
            runResourceFileAction(
                action = {
                    resourceFileUseCase.renameCustom(
                        previousFile = file,
                        customFile = nextFile,
                        customResourceFiles = nextCustomResourceFiles,
                    )
                },
                successMessage = null,
            )
        }
        return true
    }

    fun deleteCustomResourceFile(file: CustomResourceFileState) {
        runResourceFileAction(
            action = {
                var remainingCustomFiles = emptyList<CustomResourceFileState>()
                updateResourceSettings { settings ->
                    remainingCustomFiles = settings.customFiles.filterNot { it.id == file.id }
                    settings.copy(customFiles = remainingCustomFiles)
                }
                resourceFileUseCase.deleteCustom(file, remainingCustomFiles)
            },
            successMessage = deletedMessage.formatTemplate("name" to file.name),
        )
    }

    fun requestCustomResourceFileDeletion(file: CustomResourceFileState) {
        if (appState.enableDeletionConfirmation) {
            pendingCustomResourceFileDeletion = file
        } else {
            deleteCustomResourceFile(file)
        }
    }

    LaunchedEffect(resourceSettings.customFiles, updateQueueState.completionRevision) {
        status = resourceFileUseCase.status(resourceSettings.customFiles)
    }
    LaunchedEffect(resourceFileUpdateCoordinator, updatedMessage, updatedOneMessage) {
        resourceFileUpdateCoordinator.results.collect { result ->
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
                    tipNotifier.show(message)
                }
                is ResourceFileUpdateResult.Failure -> tipNotifier.showError(result.error)
                is ResourceFileUpdateResult.Cancelled -> Unit
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(R.string.configs_resources),
                isWideScreen = isWideScreen,
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackNavigationIcon(onClick = { navigator.pop() })
                },
                actions = {
                    NavigationIcon(
                        onClick = {
                            customResourceFileNameState.clearText()
                            customResourceFileUrlState.clearText()
                            showCustomResourceFileDialog.value = true
                        },
                        imageVector = MiuixIcons.Add,
                        contentDescription = stringResource(R.string.settings_resource_files_add_custom),
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
                .fillMaxSize()
                .background(AppTheme.colors.background),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = listPadding,
            ) {
                item(key = "resource_files_title") {
                    SmallTitle(text = stringResource(R.string.settings_resource_files_files))
                }
                item(key = "resource_files_source") {
                    ResourceFileSourceCard(
                        sourceOptions = sourceOptions,
                        selectedSource = resourceSettings.source,
                        selectedUpdateSource = resourceSettings.resourceFileUpdateSource(),
                        customGeoIpUrl = resourceSettings.customGeoIpUrl,
                        customGeoSiteUrl = resourceSettings.customGeoSiteUrl,
                        customGeoIpOnlyCnPrivateUrl = resourceSettings.customGeoIpOnlyCnPrivateUrl,
                        customDirectCidrIpv4Url = resourceSettings.customDirectCidrIpv4Url,
                        customDirectCidrIpv6Url = resourceSettings.customDirectCidrIpv6Url,
                        userAgent = resourceSettings.userAgent,
                        userAgentPresets = appState.subscriptionUserAgents,
                        updating = updateQueueState.isBusy,
                        actionsEnabled = !resourceActionRunning,
                        onSourceChange = { index ->
                            updateResourceSettings { settings ->
                                settings.copy(source = index.coerceIn(sourceOptions.indices))
                            }
                        },
                        onCustomSourceChange = {
                                geoIpUrl,
                                geoSiteUrl,
                                geoIpOnlyCnPrivateUrl,
                                directCidrIpv4Url,
                                directCidrIpv6Url,
                            ->
                            updateResourceSettings { settings ->
                                settings.copy(
                                    customGeoIpUrl = geoIpUrl,
                                    customGeoSiteUrl = geoSiteUrl,
                                    customGeoIpOnlyCnPrivateUrl = geoIpOnlyCnPrivateUrl,
                                    customDirectCidrIpv4Url = directCidrIpv4Url,
                                    customDirectCidrIpv6Url = directCidrIpv6Url,
                                )
                            }
                        },
                        onUserAgentChange = { userAgent ->
                            updateResourceSettings { settings ->
                                settings.copy(userAgent = userAgent)
                            }
                        },
                        onUpdate = {
                            resourceFileUpdateCoordinator.enqueue(
                                ResourceFileUpdateRequest.All(
                                    source = resourceSettings.resourceFileUpdateSource(),
                                    options = appState.resourceFileUpdateOptions(resourceSettings.userAgent),
                                    customResourceFiles = resourceSettings.customFiles,
                                ),
                            )
                        },
                        onCancel = resourceFileUpdateCoordinator::cancelAll,
                    )
                }
                item(key = "geo_auto_update") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 6.dp),
                        colors = CardDefaults.defaultColors(
                            color = AppTheme.colors.surface,
                            contentColor = AppTheme.colors.onSurface,
                        ),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                title = stringResource(R.string.configs_geo_auto_update),
                                summary = stringResource(R.string.configs_geo_auto_update_summary),
                                checked = resourceSettings.autoUpdate,
                                onCheckedChange = { checked ->
                                    updateResourceSettings { settings ->
                                        settings.copy(autoUpdate = checked)
                                    }
                                },
                            )
                            if (resourceSettings.autoUpdate) {
                                TextField(
                                    state = rememberTextFieldState(initialText = resourceSettings.updateInterval),
                                    inputTransformation = InputTransformation
                                        .byValue { _, proposed ->
                                            sanitizeSubscriptionIntervalInput(proposed.toString())
                                        }
                                        .then {
                                            val nextInterval = asCharSequence().toString()
                                            updateResourceSettings { settings ->
                                                settings.copy(updateInterval = nextInterval)
                                            }
                                        },
                                    label = stringResource(R.string.configs_geo_auto_update_interval),
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
                            updateState = updateQueueState.displayStateOf(
                                ResourceFileUpdateTarget.BuiltIn(kind),
                            ),
                            actionsEnabled = !resourceActionRunning,
                            onUpdate = { updateResourceFile(kind) },
                            onReplace = {
                                runResourceFileAction(
                                    action = { resourceFileUseCase.replace(kind, resourceSettings.customFiles) },
                                    successMessage = replacedMessage.formatTemplate("name" to kind.displayName),
                                )
                            },
                            onRestore = {
                                runResourceFileAction(
                                    action = { resourceFileUseCase.restoreBundled(kind, resourceSettings.customFiles) },
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
                            updateState = updateQueueState.displayStateOf(
                                ResourceFileUpdateTarget.Custom(customFile.id),
                            ),
                            actionsEnabled = !resourceActionRunning,
                            onUpdate = { file -> updateCustomResourceFile(file) },
                            onReplace = { file ->
                                runResourceFileAction(
                                    action = {
                                        resourceFileUseCase.replaceCustom(
                                            customFile = file,
                                            customResourceFiles = resourceSettings.customFiles,
                                        )
                                    },
                                    successMessage = replacedMessage.formatTemplate("name" to file.name),
                                )
                            },
                            onEdit = { file ->
                                editCustomResourceFileNameState.setTextAndPlaceCursorAtEnd(file.name)
                                editCustomResourceFileUrlState.setTextAndPlaceCursorAtEnd(file.url)
                                editingCustomResourceFile = file
                            },
                            onDelete = ::requestCustomResourceFileDeletion,
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
            show = showCustomResourceFileDialog.value,
            nameState = customResourceFileNameState,
            urlState = customResourceFileUrlState,
            onDismissRequest = { showCustomResourceFileDialog.value = false },
            onSave = ::addCustomResourceFile,
        )
        CustomResourceFileEditorDialog(
            show = editingCustomResourceFile != null,
            nameState = editCustomResourceFileNameState,
            urlState = editCustomResourceFileUrlState,
            onDismissRequest = { editingCustomResourceFile = null },
            onSave = { name, url ->
                editingCustomResourceFile?.let { file -> editCustomResourceFile(file, name, url) } ?: false
            },
        )
        pendingCustomResourceFileDeletion?.let { file ->
            DeleteConfirmationDialog(
                show = true,
                title = stringResource(R.string.deletion_confirmation_delete_resource_file),
                onDismissRequest = { pendingCustomResourceFileDeletion = null },
                onConfirm = {
                    pendingCustomResourceFileDeletion = null
                    deleteCustomResourceFile(file)
                },
            )
        }
    }
}

internal fun AppState.resourceFileUpdateOptions(userAgent: String): ResourceFileUpdateOptions {
    return ResourceFileUpdateOptions(
        useRunningProxy = proxyRunning,
        fallbackProxyPort = localProxyPort.toPortOrNull(),
        fallbackProxyUsername = localProxyUsername,
        fallbackProxyPassword = localProxyPassword,
        userAgent = userAgent,
    )
}

internal fun TrafficConfigResourceSettings.resourceFileUpdateSource(): ResourceFileUpdateSource {
    if (source != ResourceFileSourceCustom) {
        return ResourceFileUpdateSources.getOrElse(source) { ResourceFileUpdateSources.first() }
    }
    val fallback = ResourceFileUpdateSources.first()
    return ResourceFileUpdateSource(
        id = ResourceFileSourceCustom,
        geoIpUrl = customGeoIpUrl.trim().ifBlank { fallback.geoIpUrl },
        geoSiteUrl = customGeoSiteUrl.trim().ifBlank { fallback.geoSiteUrl },
        geoIpOnlyCnPrivateUrl = customGeoIpOnlyCnPrivateUrl.trim().ifBlank { fallback.geoIpOnlyCnPrivateUrl },
        directCidrIpv4Url = customDirectCidrIpv4Url.trim().ifBlank { fallback.directCidrIpv4Url },
        directCidrIpv6Url = customDirectCidrIpv6Url.trim().ifBlank { fallback.directCidrIpv6Url },
    )
}

private fun ResourceFilesStatus.statusOf(customFile: CustomResourceFileState): CustomResourceFileStatus {
    return customResourceFiles.firstOrNull { fileStatus -> fileStatus.file.id == customFile.id }
        ?: CustomResourceFileStatus(file = customFile)
}
