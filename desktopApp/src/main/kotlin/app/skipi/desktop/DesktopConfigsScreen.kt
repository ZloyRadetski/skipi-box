// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.analyzeShadowrocketConfig
import features.config.defaultShadowrocketConfig
import app.skipi.ui.config.SkipiTrafficConfigContextMenu
import app.skipi.ui.config.SkipiTrafficConfigProfileCard
import app.skipi.ui.config.SkipiTrafficConfigRawEditor
import app.skipi.ui.config.SkipiTrafficConfigRawEditorLabels
import app.skipi.ui.config.SkipiTrafficConfigRawEditorState
import app.skipi.ui.config.SkipiTrafficConfigUrlImportDialog
import app.skipi.ui.config.TrafficConfigCatalogProfileItem
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_copy_name
import app.skipi.ui.resources.configs_imported_name
import app.skipi.ui.resources.configs_new_name
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.time.Duration
import features.config.ConfigProfile

private val ConfigBackground: Color
    @Composable get() = DesktopContentBackground
private val ConfigSurface = Color(0xFF202126)
private val ConfigRaisedSurface = Color(0xFF292B31)
private val ConfigBorder = Color(0xFF3C3E45)
private val ConfigText = Color(0xFFF4F4F6)
private val ConfigMuted = Color(0xFFA2A5AF)
private val ConfigGreen = Color(0xFF58D27A)
private val ConfigRed = Color(0xFFFF5B62)

private data class DesktopConfigDraft(
    val id: Int?,
    val name: String,
    val content: String,
    val sourceUrl: String,
    val updateLocked: Boolean,
    val lastUpdatedAtMillis: Long,
)

private class DesktopConfigEditorSaveRejected(message: String) : RuntimeException(message)

/** Desktop counterpart of Android's TrafficConfigPage and raw configuration editor. */
@Composable
internal fun DesktopConfigsScreen(
    configLibrary: DesktopConfigLibrary,
    onTransformConfigLibrary: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> DesktopConfigLibrary,
    fetchUserAgent: String,
    fetchTimeoutSeconds: Int,
    contentPadding: PaddingValues,
) {
    val scope = rememberCoroutineScope()
    val fetcher = remember { DesktopSubscriptionFetcher() }
    var editorDraft by remember { mutableStateOf<DesktopConfigDraft?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var importUrlDialog by remember { mutableStateOf(false) }
    var importUrl by remember { mutableStateOf("") }
    var pendingDeletion by remember { mutableStateOf<DesktopStoredConfig?>(null) }
    var contextMenuConfig by remember { mutableStateOf<DesktopStoredConfig?>(null) }
    var message by remember { mutableStateOf("") }
    var updatingConfigIds by remember { mutableStateOf(emptySet<Int>()) }
    val newProfileName = stringResource(Res.string.configs_new_name, configLibrary.nextConfigId)
    val importedProfileName = stringResource(Res.string.configs_imported_name)

    fun openConfigEditor(config: DesktopStoredConfig) {
        editorDraft = DesktopConfigDraft(
            id = config.id,
            name = config.name,
            content = config.content,
            sourceUrl = config.sourceUrl,
            updateLocked = config.updateLocked,
            lastUpdatedAtMillis = config.lastUpdatedAtMillis,
        )
    }

    fun persist(
        transform: (DesktopConfigLibrary) -> DesktopConfigLibrary,
        successMessage: String,
        failurePrefix: String = "Не удалось сохранить конфиги: ",
        afterSave: (() -> Unit)? = null,
    ): Boolean {
        return runCatching { onTransformConfigLibrary(transform) }.fold(
            onSuccess = {
                message = successMessage
                afterSave?.invoke()
                true
            },
            onFailure = { error ->
                message = if (error is DesktopConfigEditorSaveRejected) {
                    "Конфиг не сохранён: ${error.message.orEmpty()}"
                } else {
                    "$failurePrefix${error.message.orEmpty()}"
                }
                false
            },
        )
    }

    fun importRawConfig(content: String, name: String, sourceUrl: String = "") {
        persist(
            transform = { latest ->
                DesktopConfigLibraries.importDocument(
                    library = latest,
                    content = content,
                    activate = false,
                    fallbackName = name,
                    sourceUrl = sourceUrl,
                )
            },
            successMessage = "Конфиг добавлен в библиотеку.",
            failurePrefix = "Конфиг не импортирован: ",
        )
    }

    fun fetchConfigFromUrl(
        url: String,
        updatingConfigId: Int? = null,
        onSuccess: (normalizedUrl: String, content: String) -> Unit,
    ) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            message = "Ссылка на источник пуста."
            return
        }
        scope.launch {
            message = "Загрузка конфига…"
            updatingConfigId?.let { updatingConfigIds = updatingConfigIds + it }
            try {
                val fetched = runCatching {
                    withContext(Dispatchers.IO) {
                        fetcher.fetch(
                            url = normalizedUrl,
                            userAgent = fetchUserAgent,
                            timeout = Duration.ofSeconds(fetchTimeoutSeconds.toLong()),
                        ).body
                    }
                }
                fetched.onSuccess { content -> onSuccess(normalizedUrl, content) }
                    .onFailure { error -> message = "Не удалось загрузить конфиг: ${error.message.orEmpty()}" }
            } finally {
                updatingConfigId?.let { updatingConfigIds = updatingConfigIds - it }
            }
        }
    }

    fun importUrlFromClipboard(url: String) {
        fetchConfigFromUrl(url) { normalizedUrl, content ->
            persist(
                transform = { latest ->
                    DesktopConfigLibraries.importDocument(
                        library = latest,
                        content = content,
                        activate = false,
                        fallbackName = importedProfileName,
                        sourceUrl = normalizedUrl,
                    )
                },
                successMessage = "Конфиг импортирован.",
                failurePrefix = "Конфиг не импортирован: ",
            )
        }
    }

    fun createConfigFromUrl(url: String) {
        fetchConfigFromUrl(url) { normalizedUrl, content ->
            val normalizedContent = content.trimEnd().plus("\n")
            val error = normalizedContent.analyzeShadowrocketConfig().diagnostics.firstOrNull {
                it.severity == ShadowrocketConfigDiagnosticSeverity.Error
            }
            if (error != null) {
                message = "Конфиг не импортирован: ${error.message}"
                return@fetchConfigFromUrl
            }
            val profile = ConfigProfile(
                id = 0,
                name = importedProfileName,
                content = normalizedContent,
                sourceUrl = normalizedUrl,
                lastUpdatedAtMillis = System.currentTimeMillis(),
            )
            persist(
                transform = { latest -> DesktopConfigLibraries.create(latest, profile) },
                successMessage = "Конфиг импортирован.",
                failurePrefix = "Конфиг не импортирован: ",
            )
        }
    }

    fun refreshConfigFromUrl(config: DesktopStoredConfig) {
        if (config.updateLocked) {
            message = "Обновление «${config.name}» заблокировано. Разблокируйте его в редакторе профиля."
            return
        }
        fetchConfigFromUrl(config.sourceUrl, updatingConfigId = config.id) { normalizedUrl, content ->
            val normalizedContent = content.trimEnd().plus("\n")
            val error = normalizedContent.analyzeShadowrocketConfig().diagnostics.firstOrNull {
                it.severity == ShadowrocketConfigDiagnosticSeverity.Error
            }
            if (error != null) {
                message = "Конфиг не импортирован: ${error.message}"
                return@fetchConfigFromUrl
            }
            val profileMetadata = normalizedContent.readDesktopSkipiProfileMetadata()
            persist(
                transform = { latest ->
                    val current = latest.configs.firstOrNull { it.id == config.id } ?: return@persist latest
                    DesktopConfigLibraries.update(
                        library = latest,
                        id = config.id,
                        name = profileMetadata.name ?: current.name,
                        content = normalizedContent,
                        sourceUrl = normalizedUrl,
                        updateLocked = profileMetadata.updateLocked ?: current.updateLocked,
                        lastUpdatedAtMillis = System.currentTimeMillis(),
                    )
                },
                successMessage = "Конфиг обновлён.",
                failurePrefix = "Конфиг не обновлён: ",
            )
        }
    }

    if (editorDraft != null) {
        DesktopConfigEditor(
            draft = editorDraft!!,
            contentPadding = contentPadding,
            onBack = { editorDraft = null },
            onSave = { name, content, sourceUrl, updateLocked ->
                val draft = editorDraft ?: return@DesktopConfigEditor false
                persist(
                    transform = { latest ->
                        when (
                            val save = applyDesktopConfigEditorSave(
                                library = latest,
                                id = draft.id,
                                lastUpdatedAtMillis = draft.lastUpdatedAtMillis,
                                name = name,
                                content = content,
                                sourceUrl = sourceUrl,
                                updateLocked = updateLocked,
                            )
                        ) {
                            is DesktopConfigEditorSaveResult.Rejected -> throw DesktopConfigEditorSaveRejected(save.message)
                            is DesktopConfigEditorSaveResult.Saved -> save.library
                        }
                    },
                    successMessage = "Конфиг сохранён.",
                    afterSave = { editorDraft = null },
                )
            },
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ConfigBackground)
            .padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 980.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Конфиги", color = ConfigText, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("Профили трафика SKIPI", color = ConfigMuted, fontSize = 14.sp)
                }
                Box {
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Outlined.Add, "Добавить конфиг", tint = ConfigText, modifier = Modifier.size(32.dp))
                    }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Новый конфиг") },
                            leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                editorDraft = DesktopConfigDraft(
                                    id = null,
                                    name = newProfileName,
                                    content = defaultShadowrocketConfig(),
                                    sourceUrl = "",
                                    updateLocked = false,
                                    lastUpdatedAtMillis = 0L,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Открыть .conf") },
                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                DesktopProfileFiles.chooseProfileToOpen()?.let { path ->
                                    DesktopProfileFiles.read(path).onSuccess { content ->
                                        importRawConfig(content, path.fileName.toString().substringBeforeLast('.'))
                                    }.onFailure { error ->
                                        message = "Не удалось открыть файл: ${error.message.orEmpty()}"
                                    }
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Импортировать из буфера") },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                            onClick = {
                                showAddMenu = false
                                val text = runCatching {
                                    Toolkit.getDefaultToolkit().systemClipboard
                                        .getData(DataFlavor.stringFlavor)
                                        .toString()
                                }.getOrDefault("").trim()
                                when {
                                    text.startsWith("http://", true) || text.startsWith("https://", true) -> importUrlFromClipboard(text)
                                    text.isNotBlank() -> importRawConfig(text, "Конфиг из буфера")
                                    else -> message = "Буфер обмена пуст."
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Импортировать по URL") },
                            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                            onClick = { showAddMenu = false; importUrlDialog = true },
                        )
                    }
                }
            }

            ConfigLibrarySummary(configLibrary)

            if (configLibrary.configs.isEmpty()) {
                EmptyConfigsCard(onCreate = {
                    editorDraft = DesktopConfigDraft(
                        id = null,
                        name = newProfileName,
                        content = defaultShadowrocketConfig(),
                        sourceUrl = "",
                        updateLocked = false,
                        lastUpdatedAtMillis = 0L,
                    )
                })
            } else {
                Text("Профили", color = ConfigText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                configLibrary.configs.forEach { config ->
                    DesktopConfigProfileCard(
                        config = config,
                        selected = config.id == configLibrary.selectedConfigId,
                        updating = config.id in updatingConfigIds,
                        canDelete = configLibrary.configs.size > 1,
                        onSelect = {
                            persist(
                                transform = { latest -> DesktopConfigLibraries.select(latest, config.id) },
                                successMessage = "Выбран «${config.name}».",
                            )
                        },
                        onEdit = { openConfigEditor(config) },
                        onRefresh = { refreshConfigFromUrl(config) },
                        onCopy = {
                            runCatching {
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(config.content), null)
                            }.onSuccess {
                                message = "Содержимое конфига скопировано."
                            }.onFailure { error ->
                                message = "Не удалось скопировать конфиг: ${error.message.orEmpty()}"
                            }
                        },
                        onDelete = { pendingDeletion = config },
                        onOpenContextMenu = { contextMenuConfig = config },
                    )
                }
            }

            if (message.isNotBlank()) {
                Text(message, color = ConfigMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    SkipiTrafficConfigUrlImportDialog(
        show = importUrlDialog,
        url = importUrl,
        onUrlChange = { importUrl = it },
        onDismissRequest = { importUrlDialog = false },
        onImport = { url ->
            importUrl = ""
            importUrlDialog = false
            createConfigFromUrl(url)
        },
    )
    contextMenuConfig?.let { config ->
        val duplicateName = stringResource(Res.string.configs_copy_name, config.name)
        SkipiTrafficConfigContextMenu(
            show = true,
            onDismissRequest = { contextMenuConfig = null },
            showRawEdit = true,
            showUiEdit = false,
            showDuplicate = true,
            showExport = true,
            showExportBase64 = false,
            showDelete = true,
            showEnable = true,
            onUpdate = if (config.sourceUrl.isNotBlank() && !config.updateLocked && config.id !in updatingConfigIds) {
                { contextMenuConfig = null; refreshConfigFromUrl(config) }
            } else null,
            onRawEdit = { contextMenuConfig = null; openConfigEditor(config) },
            onUiEdit = { contextMenuConfig = null; openConfigEditor(config) },
            onDuplicate = {
                contextMenuConfig = null
                var copy: DesktopStoredConfig? = null
                persist(
                    transform = { latest ->
                        DesktopConfigLibraries.duplicate(
                            library = latest,
                            sourceProfile = config,
                            name = duplicateName,
                        ).also { copy = it.configs.last() }
                    },
                    successMessage = "",
                    afterSave = { copy?.let(::openConfigEditor) },
                )
            },
            onExport = {
                contextMenuConfig = null
                DesktopProfileFiles.chooseProfileToSave()?.let { path ->
                    DesktopProfileFiles.write(path, config.content).onSuccess {
                        message = "Конфиг экспортирован: ${path.fileName}"
                    }.onFailure { error -> message = "Не удалось экспортировать конфиг: ${error.message.orEmpty()}" }
                }
            },
            onExportBase64 = { contextMenuConfig = null },
            onDelete = { contextMenuConfig = null; pendingDeletion = config },
            onEnable = {
                contextMenuConfig = null
                persist(
                    transform = { latest -> DesktopConfigLibraries.select(latest, config.id) },
                    successMessage = "Выбран «${config.name}».",
                )
            },
        )
    }
    pendingDeletion?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Удалить конфиг?") },
            text = { Text("«${config.name}» будет удалён из локальной библиотеки.") },
            confirmButton = {
                Button(onClick = {
                    persist(
                        transform = { latest -> DesktopConfigLibraries.remove(latest, config.id) },
                        successMessage = "Конфиг удалён.",
                    )
                    pendingDeletion = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ConfigLibrarySummary(library: DesktopConfigLibrary) {
    val active = library.configs.firstOrNull { it.id == library.selectedConfigId }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ConfigSurface),
        border = BorderStroke(1.dp, ConfigBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(ConfigRaisedSurface),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Description, contentDescription = null, tint = ConfigText) }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(active?.name ?: "Активный конфиг не выбран", color = ConfigText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (active == null) "Профилей: ${library.configs.size}" else "Применяется при следующем подключении Xray",
                    color = ConfigMuted,
                    fontSize = 14.sp,
                )
            }
            Surface(color = ConfigGreen.copy(alpha = 0.18f), shape = RoundedCornerShape(8.dp)) {
                Text("SKIPI", color = ConfigGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun EmptyConfigsCard(onCreate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ConfigSurface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Пока нет конфигов", color = ConfigText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Создайте профиль, откройте .conf или импортируйте ссылку.", color = ConfigMuted)
            Button(onClick = onCreate) { Text("Новый конфиг") }
        }
    }
}

@Composable
private fun DesktopConfigProfileCard(
    config: DesktopStoredConfig,
    selected: Boolean,
    updating: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onOpenContextMenu: () -> Unit,
) {
    val analysis = remember(config.content) { config.content.analyzeShadowrocketConfig() }
    SkipiTrafficConfigProfileCard(
        item = TrafficConfigCatalogProfileItem(
            id = config.id,
            name = config.name,
            sourceUrl = config.sourceUrl,
            active = selected,
            updating = updating,
            canDelete = canDelete,
        ),
        onSelect = onSelect,
        onEdit = onEdit,
        onDelete = onDelete,
        onUpdate = onRefresh,
        onLongPress = { onOpenContextMenu() },
        showUpdateAction = !config.updateLocked,
        supportingContent = {
            Text(
                config.sourceUrl.ifBlank { "Локальный профиль" },
                color = ConfigMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Правила: ${analysis.rules.size} · Группы: ${analysis.proxyGroups.size}", color = ConfigMuted, fontSize = 12.sp)
                if (config.updateLocked) Text(" · Обновление заблокировано", color = ConfigMuted, fontSize = 12.sp)
                if (analysis.diagnostics.isNotEmpty()) Text(" · Диагностика: ${analysis.diagnostics.size}", color = ConfigRed, fontSize = 12.sp)
            }
        },
        trailingActions = {
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, "Копировать содержимое") }
            IconButton(onClick = onOpenContextMenu) { Icon(Icons.Outlined.MoreVert, "Ещё") }
        },
    )
}

@Composable
private fun ConfigChip(text: String, accent: Color? = null) {
    Surface(
        color = (accent ?: ConfigMuted).copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text,
            color = accent ?: ConfigMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun DesktopConfigEditor(
    draft: DesktopConfigDraft,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (name: String, content: String, sourceUrl: String, updateLocked: Boolean) -> Boolean,
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var sourceUrl by remember(draft.id) { mutableStateOf(draft.sourceUrl) }
    var updateLocked by remember(draft.id) { mutableStateOf(draft.updateLocked) }
    var content by remember(draft.id) { mutableStateOf(draft.content) }
    val analysis = remember(content) { content.analyzeShadowrocketConfig() }
    SkipiTrafficConfigRawEditor(
        state = SkipiTrafficConfigRawEditorState(
            configId = draft.id,
            name = name,
            sourceUrl = sourceUrl,
            updateLocked = updateLocked,
            content = content,
            ruleCount = analysis.rules.size,
            proxyGroupCount = analysis.proxyGroups.size,
            diagnostics = analysis.diagnostics,
        ),
        labels = SkipiTrafficConfigRawEditorLabels(
            newConfigTitle = "Новый конфиг",
            editConfigTitle = "Редактор конфига",
            subtitle = "Совместимый Shadowrocket / SKIPI .conf",
            save = "Сохранить",
            name = "Название",
            sourceUrl = "Источник URL (необязательно)",
            lockUpdates = "Блокировать обновление",
            lockUpdatesSummary = "URL-профиль нельзя обновить, пока переключатель включён.",
            parsedTitle = "Конфиг разобран",
            invalidTitle = "В конфиге есть ошибки",
            rules = "Правила",
            proxyGroups = "Группы",
            diagnostics = "Диагностика",
            content = "Содержимое .conf",
        ),
        padding = contentPadding,
        isWideScreen = true,
        onNameChange = { name = it },
        onSourceUrlChange = { sourceUrl = it },
        onUpdateLockedChange = { updateLocked = it },
        onContentChange = { content = it },
        onBack = onBack,
        onSave = { onSave(name, content, sourceUrl, updateLocked) },
    )
}
