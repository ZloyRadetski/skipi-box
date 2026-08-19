// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import ui.components.AppSlider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.AppState
import app.generateRandomProxyCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.icons.IconsEye
import ui.icons.IconsEyeOff
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopSettingsScreen(
    appState: AppState,
    onUpdateAppState: ((AppState) -> AppState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    var copiedMessage by remember { mutableStateOf("") }

    val currentTimeoutSeconds = ((appState.subscriptionPingTimeoutMillis.toIntOrNull() ?: 5000) / 1000).coerceIn(1, 30)
    var pingSliderValue by remember(currentTimeoutSeconds) { mutableFloatStateOf(currentTimeoutSeconds.toFloat()) }

    val urlPresets = listOf(
        "Cloudflare" to "http://cp.cloudflare.com/generate_204",
        "Apple" to "http://captive.apple.com/hotspot-detect.html",
        "Google" to "http://www.google.com/generate_204",
    )

    fun copyToClipboard(text: String, label: String) {
        runCatching {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            copiedMessage = "$label скопирован в буфер!"
            scope.launch {
                delay(2500)
                if (copiedMessage.startsWith(label)) {
                    copiedMessage = ""
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Настройки",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
            )

            if (copiedMessage.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = copiedMessage,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Network Section
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Сеть и порты",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Динамический порт
                SettingsToggleRow(
                    title = "Динамический порт",
                    subtitle = "Автоматически подбирать свободный порт при каждом запуске",
                    checked = appState.enableDynamicLocalProxyPort,
                    onCheckedChange = { checked ->
                        onUpdateAppState { it.copy(enableDynamicLocalProxyPort = checked) }
                    }
                )

                // 2. Порт локального прокси (только если динамический порт выключен)
                AnimatedVisibility(
                    visible = !appState.enableDynamicLocalProxyPort,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        TextField(
                            value = appState.localProxyPort,
                            onValueChange = { port ->
                                onUpdateAppState { it.copy(localProxyPort = port.filter(Char::isDigit)) }
                            },
                            label = "Локальный SOCKS5 порт",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Proxy Authentication Section
        var isPasswordRevealed by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingsToggleRow(
                    title = "Включить авторизацию",
                    subtitle = "Запрашивать логин и пароль для входящих соединений SOCKS5 и HTTP",
                    checked = appState.enableLocalProxyAuth,
                    onCheckedChange = { checked ->
                        onUpdateAppState { it.copy(enableLocalProxyAuth = checked) }
                    }
                )

                if (appState.enableLocalProxyAuth) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Учётные данные",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Нажмите на строку для копирования",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }

                        TextButton(
                            text = "Сгенерировать",
                            onClick = {
                                onUpdateAppState {
                                    it.copy(
                                        localProxyUsername = generateRandomProxyCredential(),
                                        localProxyPassword = generateRandomProxyCredential(),
                                    )
                                }
                                copiedMessage = "Сгенерированы новые логин и пароль"
                                scope.launch {
                                    delay(2500)
                                    copiedMessage = ""
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Username row (tap to copy)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .clickable {
                                copyToClipboard(appState.localProxyUsername, "Логин")
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Имя пользователя",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = appState.localProxyUsername.ifBlank { "—" },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }

                        IconButton(
                            onClick = { copyToClipboard(appState.localProxyUsername, "Логин") },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Copy,
                                contentDescription = "Копировать логин",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Password row (IDENTICAL look, tap to copy without changing display, compact eye toggle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.7f))
                            .clickable {
                                copyToClipboard(appState.localProxyPassword, "Пароль")
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Пароль",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPasswordRevealed) appState.localProxyPassword.ifBlank { "—" } else "••••••••••••",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isPasswordRevealed = !isPasswordRevealed },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (isPasswordRevealed) IconsEyeOff else IconsEye,
                                    contentDescription = if (isPasswordRevealed) "Скрыть пароль" else "Показать пароль",
                                    tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(2.dp))

                            IconButton(
                                onClick = { copyToClipboard(appState.localProxyPassword, "Пароль") },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Copy,
                                    contentDescription = "Копировать пароль",
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Latency Check (Ping) Section
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Тестирование задержки (Ping)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = appState.subscriptionPingUrl,
                    onValueChange = { url ->
                        onUpdateAppState { it.copy(subscriptionPingUrl = url.trim()) }
                    },
                    label = "URL для теста задержки",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Быстрый выбор провайдера:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    urlPresets.forEach { (name, presetUrl) ->
                        val isSelected = appState.subscriptionPingUrl == presetUrl
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.surface.copy(alpha = 0.8f)
                                )
                                .clickable {
                                    onUpdateAppState { it.copy(subscriptionPingUrl = presetUrl) }
                                    copiedMessage = "Выбран тест $name"
                                    scope.launch {
                                        delay(2000)
                                        copiedMessage = ""
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MiuixTheme.colorScheme.onPrimary
                                else MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Таймаут ожидания ответа",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${pingSliderValue.roundToInt()} сек",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                AppSlider(
                    value = pingSliderValue,
                    onValueChange = { newValue ->
                        pingSliderValue = newValue
                    },
                    onValueChangeFinished = {
                        val seconds = pingSliderValue.roundToInt().coerceIn(1, 30)
                        onUpdateAppState {
                            it.copy(subscriptionPingTimeoutMillis = "${seconds * 1000}")
                        }
                    },
                    valueRange = 1f..30f,
                    steps = 28,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Core Features Section
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Параметры ядра Xray",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggleRow(
                    title = "Сниффинг трафика (Sniffing)",
                    subtitle = "Определение доменов внутри TLS и HTTP соединений",
                    checked = appState.enableSniffing,
                    onCheckedChange = { checked ->
                        onUpdateAppState { it.copy(enableSniffing = checked) }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = "Мультиплексирование (Mux)",
                    subtitle = "Объединение нескольких TCP соединений в один поток",
                    checked = appState.enableMux,
                    onCheckedChange = { checked ->
                        onUpdateAppState { it.copy(enableMux = checked) }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = "Фрагментация TLS (Fragment)",
                    subtitle = "Дробление TLS ClientHello для обхода блокировок DPI",
                    checked = appState.enableFragment,
                    onCheckedChange = { checked ->
                        onUpdateAppState { it.copy(enableFragment = checked) }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingsToggleRow(
                    title = "FakeDNS",
                    subtitle = "Выделение виртуальных IP-адресов для ускорения резолва",
                    checked = appState.enableFakeDns,
                    onCheckedChange = { checked ->
                        onUpdateAppState { it.copy(enableFakeDns = checked) }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reset Section
        Card(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Сброс настроек",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Возвращает настройки приложения к исходному состоянию",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                TextButton(
                    text = "Сбросить",
                    onClick = {
                        onUpdateAppState { AppState() }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
