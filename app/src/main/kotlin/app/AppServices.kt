// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.runtime.staticCompositionLocalOf
import android.net.Uri
import data.backup.AppBackupUseCase
import engine.proxy.AndroidProxyEngine
import engine.proxy.latency.AndroidProxyLatencyTester
import features.logs.CoreLogRepository
import features.proxy.server.usecase.ProxyServerImportFileUseCase
import features.proxy.server.usecase.ProxyServiceUseCase
import features.resources.ResourceFileUpdateCoordinator
import features.resources.ResourceFileUseCase
import features.subscription.runtime.AndroidSubscriptionFetcher
import kotlinx.coroutines.CoroutineScope
import system.AndroidPackageProvider
import system.AndroidUserSpaceProvider
import ui.feedback.AndroidToastTipNotifier

internal data class AppServices(
    val appScope: CoroutineScope,
    val proxyEngine: AndroidProxyEngine,
    val userSpaces: AndroidUserSpaceProvider,
    val packageCatalog: AndroidPackageProvider,
    val resourceFileUseCase: ResourceFileUseCase,
    val resourceFileUpdateCoordinator: ResourceFileUpdateCoordinator,
    val appBackupUseCase: AppBackupUseCase,
    val subscriptionFetcher: AndroidSubscriptionFetcher,
    val qrScanner: suspend () -> String?,
    val proxyServerImportFileUseCase: ProxyServerImportFileUseCase,
    val proxyLatencyTester: AndroidProxyLatencyTester,
    val proxyServiceUseCase: ProxyServiceUseCase,
    val tipNotifier: AndroidToastTipNotifier,
    val logFileCreator: suspend (String) -> Uri?,
    val coreLogRepository: CoreLogRepository,
    val accessLogRepository: CoreLogRepository,
    val logcatRepository: CoreLogRepository,
    val requestVpnPermission: suspend (android.content.Intent) -> Boolean = { false },
)

internal val LocalAppServices = staticCompositionLocalOf<AppServices> {
    error("LocalAppServices is not provided")
}
