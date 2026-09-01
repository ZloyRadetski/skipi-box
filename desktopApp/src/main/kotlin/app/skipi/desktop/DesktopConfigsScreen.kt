// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.config.ShadowrocketConfigAnalysis
import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.analyzeShadowrocketConfig
import features.config.defaultShadowrocketConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.time.Duration

private val ConfigBackground: Color
    @Composable get() = DesktopContentBackground
private val ConfigSurface = Color(0xFF202126)
private val ConfigRaisedSurface = Color(0xFF292B31)
private val ConfigBorder = Color(0xFF3C3E45)
private val ConfigText = Color(0xFFF4F4F6)
private val ConfigMuted = Color(0xFFA2A5AF)
private val ConfigGreen = Color(0xFF58D27A)
private val ConfigRed = Color(0xFFFF5B62)
private val ConfigSelected = Color(0xFF737373)

private data class DesktopConfigDraft(
    val id: Int?,
    val name: String,
    val content: String,
    val sourceUrl: String,
    val updateLocked: Boolean,
    val lastUpdatedAtMillis: Long,
)

/** Desktop counterpart of Android's TrafficConfigPage and raw configuration editor. */
@Composable
internal fun DesktopConfigsScreen(
    configLibrary: DesktopConfigLibrary,
    onConfigLibraryChange: (DesktopConfigLibrary) -> Unit,
    fetchUserAgent: String,
    fetchTimeoutSeconds: Int,
    contentPadding: PaddingValues,
) {
    val scope = rememberCoroutineScope()
    val fetcher = remember { DesktopSubscriptionFetcher() }
    var editorDraft by remember { mutableStateOf<DesktopConfigDraft?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var importUrlDialog by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<DesktopStoredConfig?>(null) }
    var message by remember { mutableStateOf("") }
    var updatingConfigIds by remember { mutableStateOf(emptySet<Int>()) }

    fun persist(updated: DesktopConfigLibrary, successMessage: String, afterSave: (() -> Unit)? = null): Boolean {
        return DesktopConfigLibraries.saveDefault(updated).fold(
            onSuccess = {
                onConfigLibraryChange(updated)
                message = successMessage
                afterSave?.invoke()
                true
            },
            onFailure = { error ->
                message = "Не удалось сохранить конфиги: ${error.message.orEmpty()}"
                false
            },
        )
    }

    fun importRawConfig(content: String, name: String, sourceUrl: String = "") {
        val normalized = content.trimEnd().plus("\n")
        val analysis = normalized.analyzeShadowrocketConfig()
        val error = analysis.diagnostics.firstOrNull { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }
        if (error != null) {
            message = "Конфиг не импортирован: ${error.message}"
            return
        }
        val profileMetadata = normalized.readDesktopSkipiProfileMetadata()
        val updated = DesktopConfigLibraries.put(
            library = configLibrary,
            name = profileMetadata.name ?: name,
            content = normalized,
            sourceUrl = sourceUrl.trim().takeIf(String::isNotBlank) ?: profileMetadata.sourceUrl,
            updateLocked = profileMetadata.updateLocked,
        )
        persist(updated, "Конфиг добавлен в библиотеку.")
    }

    fun importFromUrl(url: String, existing: DesktopStoredConfig? = null) {
        val normalizedUrl = url.trim()
        val target = existing ?: configLibrary.configs.firstOrNull { config ->
            config.sourceUrl.equals(normalizedUrl, ignoreCase = true)
        }
        if (target?.updateLocked == true) {
            message = "Обновление «${target.name}» заблокировано. Разблокируйте его в редакторе профиля."
            return
        }
        scope.launch {
            message = "Загрузка конфига…"
            target?.let { updatingConfigIds = updatingConfigIds + it.id }
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        fetcher.fetch(
                            url = normalizedUrl,
                            userAgent = fetchUserAgent,
                            timeout = Duration.ofSeconds(fetchTimeoutSeconds.toLong()),
                        ).body
                    }
                }.onSuccess { content ->
                    val normalizedContent = content.trimEnd().plus("\n")
                    val analysis = normalizedContent.analyzeShadowrocketConfig()
                    val error = analysis.diagnostics.firstOrNull {
                        it.severity == ShadowrocketConfigDiagnosticSeverity.Error
                    }
                    if (error != null) {
                        message = "Конфиг не импортирован: ${error.message}"
                        return@onSuccess
                    }
                    val profileMetadata = normalizedContent.readDesktopSkipiProfileMetadata()
                    val updated = if (target == null) {
                        DesktopConfigLibraries.put(
                            library = configLibrary,
                            name = profileMetadata.name ?: "Импортированный конфиг ${(configLibrary.configs.maxOfOrNull { it.id } ?: 0) + 1}",
                            content = normalizedContent,
                            sourceUrl = normalizedUrl,
                            updateLocked = profileMetadata.updateLocked,
                            lastUpdatedAtMillis = System.currentTimeMillis(),
                        )
                    } else {
                        DesktopConfigLibraries.update(
                            library = configLibrary,
                            id = target.id,
                            name = profileMetadata.name ?: target.name,
                            content = normalizedContent,
                            sourceUrl = normalizedUrl,
                            updateLocked = profileMetadata.updateLocked ?: target.updateLocked,
                            lastUpdatedAtMillis = System.currentTimeMillis(),
                        )
                    }
                    persist(updated, if (target == null) "Конфиг импортирован." else "Конфиг обновлён.")
                }.onFailure { error ->
                    message = "Не удалось загрузить конфиг: ${error.message.orEmpty()}"
                }
            } finally {
                target?.let { updatingConfigIds = updatingConfigIds - it.id }
            }
        }
    }

    if (editorDraft != null) {
        DesktopConfigEditor(
            draft = editorDraft!!,
            contentPadding = contentPadding,
            onBack = { editorDraft = null },
            onSave = { name, content, sourceUrl, updateLocked ->
                val draft = editorDraft ?: return@DesktopConfigEditor false
                val normalizedContent = content.trimEnd().plus("\n")
                    .withDesktopSkipiProfileMetadata(name, sourceUrl, updateLocked)
                val validationError = normalizedContent.analyzeShadowrocketConfig().diagnostics
                    .firstOrNull { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }
                if (validationError != null) {
                    message = "Конфиг не сохранён: ${validationError.message}"
                    return@DesktopConfigEditor false
                }
                val updated = if (draft.id == null) {
                    DesktopConfigLibraries.put(
                        library = configLibrary,
                        name = name,
                        content = normalizedContent,
                        sourceUrl = sourceUrl,
                        updateLocked = updateLocked,
                    )
                } else {
                    DesktopConfigLibraries.update(
                        library = configLibrary,
                        id = draft.id,
                        name = name,
                        content = normalizedContent,
                        sourceUrl = sourceUrl,
                        updateLocked = updateLocked,
                        lastUpdatedAtMillis = draft.lastUpdatedAtMillis,
                    )
                }
                persist(updated, "Конфиг сохранён.") { editorDraft = null }
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
                                val nextId = (configLibrary.configs.maxOfOrNull { it.id } ?: 0) + 1
                                editorDraft = DesktopConfigDraft(
                                    id = null,
                                    name = "Конфиг $nextId",
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
                                    text.startsWith("http://", true) || text.startsWith("https://", true) -> importFromUrl(text)
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
                    val nextId = (configLibrary.configs.maxOfOrNull { it.id } ?: 0) + 1
                    editorDraft = DesktopConfigDraft(
                        id = null,
                        name = "Конфиг $nextId",
                        content = defaultShadowrocketConfig(),
                        sourceUrl = "",
                        updateLocked = false,
                        lastUpdatedAtMillis = 0L,
                    )
                })
            } else {
                Text("Профили", color = ConfigText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                configLibrary.configs.forEach { config ->
                    ConfigProfileCard(
                        config = config,
                        selected = config.id == configLibrary.selectedConfigId,
                        updating = config.id in updatingConfigIds,
                        canDelete = configLibrary.configs.size > 1,
                        onSelect = {
                            persist(DesktopConfigLibraries.select(configLibrary, config.id), "Выбран «${config.name}».")
                        },
                        onEdit = {
                            editorDraft = DesktopConfigDraft(
                                id = config.id,
                                name = config.name,
                                content = config.content,
                                sourceUrl = config.sourceUrl,
                                updateLocked = config.updateLocked,
                                lastUpdatedAtMillis = config.lastUpdatedAtMillis,
                            )
                        },
                        onRefresh = { importFromUrl(config.sourceUrl, config) },
                        onDuplicate = {
                            editorDraft = DesktopConfigDraft(
                                id = null,
                                name = "${config.name} — копия",
                                content = config.content,
                                sourceUrl = config.sourceUrl,
                                updateLocked = config.updateLocked,
                                lastUpdatedAtMillis = config.lastUpdatedAtMillis,
                            )
                        },
                        onExport = {
                            DesktopProfileFiles.chooseProfileToSave()?.let { path ->
                                DesktopProfileFiles.write(path, config.content).onSuccess {
                                    message = "Конфиг экспортирован: ${path.fileName}"
                                }.onFailure { error ->
                                    message = "Не удалось экспортировать конфиг: ${error.message.orEmpty()}"
                                }
                            }
                        },
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
                    )
                }
            }

            if (message.isNotBlank()) {
                Text(message, color = ConfigMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (importUrlDialog) {
        DesktopConfigUrlImportDialog(
            onImport = { url ->
                importUrlDialog = false
                importFromUrl(url)
            },
            onDismiss = { importUrlDialog = false },
        )
    }
    pendingDeletion?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Удалить конфиг?") },
            text = { Text("«${config.name}» будет удалён из локальной библиотеки.") },
            confirmButton = {
                Button(onClick = {
                    val updated = DesktopConfigLibraries.remove(configLibrary, config.id)
                    persist(updated, "Конфиг удалён.")
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
private fun ConfigProfileCard(
    config: DesktopStoredConfig,
    selected: Boolean,
    updating: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val analysis = remember(config.content) { config.content.analyzeShadowrocketConfig() }
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) ConfigSelected else ConfigSurface),
        border = BorderStroke(1.dp, if (selected) Color(0xFF9A9A9A) else Color.Transparent),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(ConfigRaisedSurface),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Description, contentDescription = null, tint = ConfigText) }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(config.name, color = ConfigText, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        config.sourceUrl.ifBlank { "Локальный профиль" },
                        color = ConfigMuted,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) ConfigChip("Активен", accent = ConfigGreen)
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ConfigChip("Правила: ${analysis.rules.size}")
                Spacer(Modifier.width(7.dp))
                ConfigChip("Группы: ${analysis.proxyGroups.size}")
                if (config.updateLocked) {
                    Spacer(Modifier.width(7.dp))
                    ConfigChip("Обновление заблокировано")
                }
                if (analysis.diagnostics.isNotEmpty()) {
                    Spacer(Modifier.width(7.dp))
                    ConfigChip("Диагностика: ${analysis.diagnostics.size}", accent = ConfigRed)
                }
                Spacer(Modifier.weight(1f))
                if (config.sourceUrl.isNotBlank()) {
                    IconButton(onClick = onRefresh, enabled = !updating && !config.updateLocked) {
                        Icon(
                            if (config.updateLocked) Icons.Outlined.Lock else Icons.Outlined.Refresh,
                            if (config.updateLocked) "Обновление заблокировано" else "Обновить конфиг",
                            tint = ConfigText,
                        )
                    }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Редактировать", tint = ConfigText) }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, "Ещё", tint = ConfigText)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Дублировать") }, onClick = { menuExpanded = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Экспортировать .conf") }, onClick = { menuExpanded = false; onExport() })
                        DropdownMenuItem(text = { Text("Копировать содержимое") }, onClick = { menuExpanded = false; onCopy() })
                        DropdownMenuItem(
                            text = { Text("Удалить") },
                            enabled = canDelete,
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = ConfigRed) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
            if (updating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ConfigGreen)
        }
    }
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
private fun DesktopConfigUrlImportDialog(onImport: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импортировать конфиг") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Введите HTTP/HTTPS ссылку на .conf-профиль.", color = ConfigMuted)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("URL конфига") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onImport(url) }, enabled = url.trim().startsWith("http://", true) || url.trim().startsWith("https://", true)) {
                Text("Импортировать")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
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
    val errors = analysis.diagnostics.filter { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }

    Box(
        modifier = Modifier.fillMaxSize().background(ConfigBackground).padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().widthIn(max = 980.dp).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", tint = ConfigText) }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (draft.id == null) "Новый конфиг" else "Редактор конфига", color = ConfigText, fontSize = 25.sp, fontWeight = FontWeight.Black)
                    Text("Совместимый Shadowrocket / SKIPI .conf", color = ConfigMuted, fontSize = 14.sp)
                }
                Button(
                    onClick = { onSave(name, content, sourceUrl, updateLocked) },
                    enabled = name.isNotBlank() && content.isNotBlank() && errors.isEmpty(),
                ) {
                    Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Сохранить")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ConfigSurface),
                border = BorderStroke(1.dp, ConfigBorder),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(value = sourceUrl, onValueChange = { sourceUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Источник URL (необязательно)") }, singleLine = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Блокировать обновление", color = ConfigText, fontWeight = FontWeight.SemiBold)
                            Text("URL-профиль нельзя обновить, пока переключатель включён.", color = ConfigMuted, fontSize = 13.sp)
                        }
                        Switch(
                            checked = updateLocked,
                            onCheckedChange = { updateLocked = it },
                        )
                    }
                }
            }

            ConfigAnalysisCard(analysis, errors)

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Содержимое .conf") },
                minLines = 20,
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConfigAnalysisCard(analysis: ShadowrocketConfigAnalysis, errors: List<*>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ConfigSurface),
        border = BorderStroke(1.dp, if (errors.isEmpty()) ConfigBorder else ConfigRed.copy(alpha = 0.65f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (errors.isEmpty()) Icons.Outlined.Description else Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = if (errors.isEmpty()) ConfigGreen else ConfigRed,
                )
                Spacer(Modifier.width(9.dp))
                Text(if (errors.isEmpty()) "Конфиг разобран" else "В конфиге есть ошибки", color = ConfigText, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfigChip("Правила: ${analysis.rules.size}")
                ConfigChip("Группы: ${analysis.proxyGroups.size}")
                ConfigChip("Диагностика: ${analysis.diagnostics.size}", if (analysis.diagnostics.isEmpty()) ConfigGreen else ConfigRed)
            }
            analysis.diagnostics.take(4).forEach { diagnostic ->
                Text("${diagnostic.severity}: ${diagnostic.message}", color = if (diagnostic.severity == ShadowrocketConfigDiagnosticSeverity.Error) ConfigRed else ConfigMuted, fontSize = 13.sp)
            }
        }
    }
}
