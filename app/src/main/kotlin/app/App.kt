// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.effects.ProxyStatusSynchronizer
import app.effects.LauncherIconSynchronizer
import app.effects.ResourceFileSynchronizer
import features.logs.AndroidAccessLogRepository
import features.logs.AndroidCoreLogRepository
import features.logs.AndroidLogcatRepository
import data.backup.AppBackupUseCase
import engine.proxy.AndroidProxyEngine
import engine.proxy.latency.AndroidProxyLatencyTester
import features.proxy.server.usecase.ProxyServerImportFileUseCase
import features.proxy.server.usecase.ProxyServiceUseCase
import features.resources.ResourceFileUpdateCoordinator
import features.resources.ResourceFileUpdateRequest
import features.resources.ResourceFileUseCase
import features.resources.runtime.AndroidResourceFileDownloadCancellation
import features.settings.locale.ProvideAppLanguage
import features.settings.locale.RecreateActivityOnAppLanguageChange
import system.AndroidPackageProvider
import system.AndroidUserSpaceProvider
import ui.AppTheme
import ui.feedback.AndroidToastTipNotifier
import ui.feedback.AppHapticFeedback
import ui.feedback.ComposeAppHapticFeedback
import ui.feedback.LocalAppHaptics
import ui.keyColorFor

@Composable
fun App(
    padding: PaddingValues = PaddingValues(0.dp),
    qrCodeScanner: suspend () -> String?,
    resourceFilePicker: suspend () -> Uri?,
    photoFilePicker: suspend () -> Uri? = { null },
    logFileCreator: suspend (String) -> Uri?,
    requestVpnPermission: suspend (Intent) -> Boolean,
) {
    val appContext = LocalContext.current.applicationContext
    val systemUiSnapshot = appContext.currentSystemUiSnapshot()
    val application = appContext as SkipiApplication
    val appScope = application.appScope
    val userSpaces = remember(appContext) {
        AndroidUserSpaceProvider(context = appContext)
    }
    val packageCatalog = remember(appContext) {
        AndroidPackageProvider(context = appContext)
    }
    val resourceFileUseCase = remember(appContext, resourceFilePicker) {
        ResourceFileUseCase(
            context = appContext,
            resourceFilePicker = resourceFilePicker,
        )
    }
    val resourceFileUpdateCoordinator = remember(appScope, resourceFileUseCase) {
        ResourceFileUpdateCoordinator(
            scope = appScope,
            execute = { request ->
                when (request) {
                    is ResourceFileUpdateRequest.BuiltIn -> resourceFileUseCase.update(
                        kind = request.kind,
                        source = request.source,
                        options = request.options,
                        customResourceFiles = request.customResourceFiles,
                    )
                    is ResourceFileUpdateRequest.Custom -> resourceFileUseCase.updateCustom(
                        customFile = request.file,
                        options = request.options,
                        customResourceFiles = request.customResourceFiles,
                    )
                    is ResourceFileUpdateRequest.All -> resourceFileUseCase.update(
                        source = request.source,
                        options = request.options,
                        customResourceFiles = request.customResourceFiles,
                    )
                }
            },
            cancelRunning = AndroidResourceFileDownloadCancellation::cancel,
        )
    }
    val appBackupUseCase = remember(appContext, resourceFilePicker, logFileCreator) {
        AppBackupUseCase(
            context = appContext,
            filePicker = resourceFilePicker,
            fileCreator = logFileCreator,
        )
    }
    val subscriptionFetcher = remember(application) { application.subscriptionFetcher }
    val qrScanner = remember(qrCodeScanner) { qrCodeScanner }
    val proxyServerImportFileUseCase = remember(appContext, resourceFilePicker) {
        ProxyServerImportFileUseCase(
            context = appContext,
            filePicker = resourceFilePicker,
        )
    }
    val proxyLatencyTester = remember(appContext) {
        AndroidProxyLatencyTester(appContext)
    }
    val proxyEngine = remember(appContext) {
        AndroidProxyEngine(
            context = appContext,
            requestVpnPermission = requestVpnPermission,
        )
    }
    val proxyServiceUseCase = remember(proxyEngine) {
        ProxyServiceUseCase(proxyEngine)
    }
    val stateStore = remember(application) { application.stateStore }
    val tipNotifier = remember(appContext) { AndroidToastTipNotifier(appContext) }
    val haptics = remember(appContext) {
        AppHapticFeedback(appContext) { stateStore.currentState.enableHaptics }
    }
    val composeHaptics = remember(haptics) { ComposeAppHapticFeedback(haptics) }
    val services = remember(
        appScope,
        proxyEngine,
        userSpaces,
        packageCatalog,
        resourceFileUseCase,
        resourceFileUpdateCoordinator,
        appBackupUseCase,
        subscriptionFetcher,
        qrScanner,
        proxyServerImportFileUseCase,
        proxyLatencyTester,
        proxyServiceUseCase,
        tipNotifier,
        logFileCreator,
    ) {
        AppServices(
            appScope = appScope,
            proxyEngine = proxyEngine,
            userSpaces = userSpaces,
            packageCatalog = packageCatalog,
            resourceFileUseCase = resourceFileUseCase,
            resourceFileUpdateCoordinator = resourceFileUpdateCoordinator,
            appBackupUseCase = appBackupUseCase,
            subscriptionFetcher = subscriptionFetcher,
            qrScanner = qrScanner,
            proxyServerImportFileUseCase = proxyServerImportFileUseCase,
            proxyLatencyTester = proxyLatencyTester,
            proxyServiceUseCase = proxyServiceUseCase,
            tipNotifier = tipNotifier,
            logFileCreator = logFileCreator,
            coreLogRepository = AndroidCoreLogRepository,
            accessLogRepository = AndroidAccessLogRepository,
            logcatRepository = AndroidLogcatRepository,
            requestVpnPermission = requestVpnPermission,
            photoFilePicker = photoFilePicker,
        )
    }
    val chromeState by stateStore.collectAppChromeState()
    val updateAppState: ((AppState) -> AppState) -> Unit = remember(stateStore) {
        { transform -> stateStore.update(transform) }
    }
    val keyColor = keyColorFor(chromeState.seedIndex, chromeState.customMaterialYouSeed)
    RecreateActivityOnAppLanguageChange(languageMode = chromeState.languageMode)
    ProxyStatusSynchronizer(
        stateStore = stateStore,
        proxyEngine = proxyEngine,
        updateAppState = updateAppState,
    )
    ResourceFileSynchronizer(
        resourceFileUseCase = resourceFileUseCase,
        stateStore = stateStore,
    )
    LauncherIconSynchronizer(
        context = appContext,
        stateStore = stateStore,
    )

    ProvideAppLanguage(
        languageMode = chromeState.languageMode,
        systemLocale = systemUiSnapshot.locale,
    ) {
        AppTheme(
            colorMode = chromeState.colorMode,
            enableMaterialYou = chromeState.enableMaterialYou,
            keyColor = keyColor,
            enableCustomColors = chromeState.enableCustomColors,
            customAccentColor = chromeState.customAccentColor?.let { Color(it) },
            customBackgroundColor = chromeState.customBackgroundColor?.let { Color(it) },
            customSurfaceColor = chromeState.customSurfaceColor?.let { Color(it) },
            customSurfaceVariantColor = chromeState.customSurfaceVariantColor?.let { Color(it) },
            customTextColor = chromeState.customTextColor?.let { Color(it) },
            customTextSecondaryColor = chromeState.customTextSecondaryColor?.let { Color(it) },
            backgroundStyle = chromeState.backgroundStyle,
            systemDark = systemUiSnapshot.isDark,
        ) {
            CompositionLocalProvider(
                LocalAppStateStore provides stateStore,
                LocalAppChromeState provides chromeState,
                LocalUpdateAppState provides updateAppState,
                LocalAppServices provides services,
                LocalAppHaptics provides haptics,
                LocalHapticFeedback provides composeHaptics,
            ) {
                ui.background.AppBackground {
                    AppContent(padding = padding)
                }
            }
        }
    }
}
