// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigProfileBasicsDraft
import app.skipi.app.config.isConfigEditorBoolean
import app.skipi.ui.layout.pageScrollModifiers
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

data class SkipiTrafficConfigEditorSectionAction(
    val key: String,
    val title: String,
    val summary: String,
    val onNavigate: () -> Unit,
)

/** The shared Basics screen and section list used by Android and Desktop. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiTrafficConfigBasicsEditorScreen(
    title: String,
    sectionsTitle: String,
    sections: List<SkipiTrafficConfigEditorSectionAction>,
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
    padding: PaddingValues,
    isWideScreen: Boolean,
    saveLabel: String,
    onBack: () -> Unit,
    onSave: (() -> Unit)? = null,
    platformBackHandler: @Composable (onBack: () -> Unit) -> Unit = {},
    statusContent: (@Composable () -> Unit)? = null,
) {
    var draft by remember(profile.id) {
        mutableStateOf(
            TrafficConfigProfileBasicsDraft(
                name = profile.name,
                sourceUrl = profile.sourceUrl,
                updateLocked = profile.updateLocked,
                autoUpdate = profile.autoUpdate,
                updateInterval = profile.updateInterval,
                resourceAutoUpdate = profile.resourceSettings.autoUpdate,
                resourceUpdateInterval = profile.resourceSettings.updateInterval,
            ),
        )
    }
    val saveAndBack: () -> Unit = {
        controller.saveBasicsThenNavigate(profile, draft, onBack)
        Unit
    }
    val saveAndNavigate: (() -> Unit)? = onSave?.let { navigate ->
        {
            controller.saveBasicsThenNavigate(profile, draft, navigate)
            Unit
        }
    }
    platformBackHandler(saveAndBack)
    val listState = rememberLazyListState()
    SkipiTrafficConfigFullScreenScaffold(
        title = title,
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = saveLabel,
        onBack = saveAndBack,
        onSave = saveAndNavigate,
    ) { contentPadding, listPadding, scrollBehavior ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.pageScrollModifiers(scrollBehavior),
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "profile_basics") {
                    SkipiTrafficConfigProfileBasics(
                        name = draft.name,
                        onNameChange = { draft = draft.copy(name = it) },
                        sourceUrl = draft.sourceUrl,
                        onSourceUrlChange = { draft = draft.copy(sourceUrl = it) },
                        updateLocked = draft.updateLocked,
                        onUpdateLockedChange = { draft = draft.copy(updateLocked = it) },
                        autoUpdate = draft.autoUpdate,
                        onAutoUpdateChange = { draft = draft.copy(autoUpdate = it) },
                        updateInterval = draft.updateInterval,
                        onUpdateIntervalChange = { draft = draft.copy(updateInterval = it) },
                        resourceAutoUpdate = draft.resourceAutoUpdate,
                        onResourceAutoUpdateChange = { draft = draft.copy(resourceAutoUpdate = it) },
                        resourceUpdateInterval = draft.resourceUpdateInterval,
                        onResourceUpdateIntervalChange = { draft = draft.copy(resourceUpdateInterval = it) },
                    )
                }
                item(key = "sections_title") {
                    SmallTitle(text = sectionsTitle)
                }
                items(sections, key = SkipiTrafficConfigEditorSectionAction::key) { section ->
                    SkipiTrafficConfigEditorGroupCard(
                        title = section.title,
                        summary = section.summary,
                        onClick = {
                            controller.saveBasicsThenNavigate(profile, draft, section.onNavigate)
                            Unit
                        },
                    )
                }
                statusContent?.let { content ->
                    item(key = "editor_status") { content() }
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                trackPadding = contentPadding,
            )
        }
    }
}

/** Shared General screen; profile persistence and navigation remain host-owned. */
@Composable
fun SkipiTrafficConfigGeneralEditorScreen(
    title: String,
    padding: PaddingValues,
    isWideScreen: Boolean,
    saveLabel: String,
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
    onBack: () -> Unit,
    onSave: () -> Unit,
    platformBackHandler: @Composable (onBack: () -> Unit) -> Unit = {},
    statusContent: (@Composable () -> Unit)? = null,
) {
    val general = remember(profile.id, profile.rawConfig) {
        profile.rawConfig.analyzeShadowrocketConfig().general
    }
    var ipv6 by remember(profile.id, profile.rawConfig) {
        mutableStateOf(general["ipv6"].isConfigEditorBoolean())
    }
    var preferIpv6 by remember(profile.id, profile.rawConfig) {
        mutableStateOf(general["prefer-ipv6"].isConfigEditorBoolean())
    }
    val saveAndBack: () -> Unit = {
        controller.saveGeneralThenNavigate(profile.id, ipv6, preferIpv6, onBack)
        Unit
    }
    val saveAndNavigate: () -> Unit = {
        controller.saveGeneralThenNavigate(profile.id, ipv6, preferIpv6, onSave)
        Unit
    }
    platformBackHandler(saveAndBack)
    SkipiTrafficConfigFullScreenScaffold(
        title = title,
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = saveLabel,
        onBack = saveAndBack,
        onSave = saveAndNavigate,
    ) { _, listPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SkipiTrafficConfigGeneralOptions(
                    ipv6 = ipv6,
                    preferIpv6 = preferIpv6,
                    onIpv6Change = { ipv6 = it },
                    onPreferIpv6Change = { preferIpv6 = it },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            statusContent?.let { content ->
                item(key = "editor_status") { content() }
            }
        }
    }
}

/** Shared DNS screen; profile persistence and navigation remain host-owned. */
@Composable
fun SkipiTrafficConfigDnsEditorScreen(
    title: String,
    padding: PaddingValues,
    isWideScreen: Boolean,
    saveLabel: String,
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
    onBack: () -> Unit,
    onSave: () -> Unit,
    platformBackHandler: @Composable (onBack: () -> Unit) -> Unit = {},
    statusContent: (@Composable () -> Unit)? = null,
) {
    var settings by remember(profile.id) { mutableStateOf(profile.androidSettings) }
    val saveAndBack: () -> Unit = {
        controller.saveDnsThenNavigate(profile.id, settings, onBack)
        Unit
    }
    val saveAndNavigate: () -> Unit = {
        controller.saveDnsThenNavigate(profile.id, settings, onSave)
        Unit
    }
    platformBackHandler(saveAndBack)
    SkipiTrafficConfigFullScreenScaffold(
        title = title,
        padding = padding,
        isWideScreen = isWideScreen,
        saveLabel = saveLabel,
        onBack = saveAndBack,
        onSave = saveAndNavigate,
    ) { _, listPadding, scrollBehavior ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().pageScrollModifiers(scrollBehavior),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SkipiTrafficConfigDnsEditor(
                    settings = settings,
                    onSettingsChange = { settings = it },
                )
            }
            statusContent?.let { content ->
                item(key = "editor_status") { content() }
            }
        }
    }
}
