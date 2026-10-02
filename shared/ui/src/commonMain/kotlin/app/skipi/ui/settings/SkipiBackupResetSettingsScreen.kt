// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.skipi.ui.theme.SkipiTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference

data class BackupResetSettingsLabels(
    val screenTitle: String,
    val backupSectionTitle: String,
    val backupTitle: String,
    val backupSummary: String,
    val restoreTitle: String,
    val restoreSummary: String,
    val resetSectionTitle: String,
    val resetTunnelTitle: String,
    val resetTunnelSummary: String,
    val resetVpnTitle: String,
    val resetVpnSummary: String,
    val resetAppTitle: String,
    val resetAppSummary: String,
)

/** Shared presentation for backup and reset. The host owns persistence and destructive actions. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiBackupResetSettingsScreen(
    labels: BackupResetSettingsLabels,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onResetTunnel: () -> Unit,
    onResetVpn: () -> Unit,
    onResetApp: () -> Unit,
    overlays: @Composable () -> Unit = {},
) {
    val listState = rememberLazyListState()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            key(labels.screenTitle) {
                SettingsBackTopBar(labels.screenTitle, isWideScreen, onBack)
            }
        },
    ) { innerPadding ->
        val contentPadding = settingsPageContentPadding(innerPadding, padding, isWideScreen)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "backup_restore") {
                    SmallTitle(text = labels.backupSectionTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(title = labels.backupTitle, summary = labels.backupSummary, onClick = onBackup)
                        ArrowPreference(title = labels.restoreTitle, summary = labels.restoreSummary, onClick = onRestore)
                    }
                }
                item(key = "reset") {
                    SmallTitle(text = labels.resetSectionTitle)
                    SkipiSettingsSectionCard {
                        ArrowPreference(title = labels.resetTunnelTitle, summary = labels.resetTunnelSummary, onClick = onResetTunnel)
                        ArrowPreference(title = labels.resetVpnTitle, summary = labels.resetVpnSummary, onClick = onResetVpn)
                        ArrowPreference(title = labels.resetAppTitle, summary = labels.resetAppSummary, onClick = onResetApp)
                    }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
            overlays()
        }
    }
}
