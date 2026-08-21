// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.core.view.WindowCompat
import com.journeyapps.barcodescanner.ScanContract
import data.AndroidAppStateStore
import data.AppSettingsPreferences
import engine.proxy.AndroidProxyEngine
import engine.vpn.AndroidVpnPermissionRequester
import features.config.SkipiDeepLink
import features.config.toSkipiDeepLinkOrNull
import features.config.withImportedSkipiServer
import features.config.withImportedTrafficConfig
import features.logs.AndroidLogFileCreator
import features.proxy.server.qr.AndroidQrCodeScanRequester
import features.proxy.server.usecase.ProxyServiceResult
import features.proxy.server.usecase.ProxyServiceUseCase
import features.resources.runtime.AndroidResourceFilePicker
import features.settings.locale.localizedAppContext
import features.subscription.SubscriptionInstallConfigUseCase
import features.subscription.isSubscriptionInstallConfigUri
import features.subscription.runtime.AndroidSubscriptionFetcher
import features.subscription.subscriptionInstallMessage
import features.subscription.toSubscriptionInstallConfigOrNull
import features.subscription.usecase.toSubscriptionFetchOptions
import app.effects.resolveActiveNetworkConfig
import features.config.withActiveTrafficConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ui.feedback.AndroidToastTipNotifier

class MainActivity : ComponentActivity() {
    private val vpnPermissionRequester = AndroidVpnPermissionRequester {
        getString(R.string.error_vpn_permission_launcher_missing)
    }

    private val qrCodeScanRequester = AndroidQrCodeScanRequester(
        hasCameraPermission = {
            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        },
        permissionDeniedMessage = {
            getString(R.string.error_qr_camera_permission_denied)
        },
        missingLauncherMessage = {
            getString(R.string.error_qr_scan_launcher_missing)
        },
    )

    private val resourceFilePicker = AndroidResourceFilePicker(
        missingLauncherMessage = {
            getString(R.string.error_resource_file_picker_missing)
        },
    )

    private val photoFilePicker = AndroidResourceFilePicker(
        missingLauncherMessage = {
            getString(R.string.error_resource_file_picker_missing)
        },
    )

    private val logFileCreator = AndroidLogFileCreator(
        missingLauncherMessage = {
            getString(R.string.error_log_export_launcher_missing)
        },
    )
    private val tipNotifier by lazy { AndroidToastTipNotifier(this) }

    private val subscriptionInstallConfigUseCase by lazy {
        SubscriptionInstallConfigUseCase(
            stateStore = AndroidAppStateStore.get(this),
            subscriptionFetcher = AndroidSubscriptionFetcher(this),
        )
    }
    private val deepLinkProxyEngine by lazy {
        AndroidProxyEngine(
            context = applicationContext,
            requestVpnPermission = vpnPermissionRequester::request,
        )
    }
    private val deepLinkProxyServiceUseCase by lazy { ProxyServiceUseCase(deepLinkProxyEngine) }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        vpnPermissionRequester.complete(result.resultCode == RESULT_OK)
    }

    private val qrCodePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        qrCodeScanRequester.completeCameraPermission(granted)
    }

    private val qrCodeScanLauncher = registerForActivityResult(ScanContract()) { result ->
        qrCodeScanRequester.completeScan(result.contents)
    }

    private val resourceFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        resourceFilePicker.complete(uri)
    }

    private val photoFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        photoFilePicker.complete(uri)
    }

    private val logFileCreatorLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        logFileCreator.complete(uri)
    }

    override fun attachBaseContext(newBase: Context) {
        val settings = AppSettingsPreferences(newBase).load()
        super.attachBaseContext(
            newBase.localizedAppContext(settings.languageMode, settings.colorMode),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vpnPermissionRequester.registerLauncher { intent ->
            vpnPermissionLauncher.launch(intent)
        }
        qrCodeScanRequester.registerPermissionLauncher { permission ->
            qrCodePermissionLauncher.launch(permission)
        }
        qrCodeScanRequester.registerScanLauncher { options ->
            qrCodeScanLauncher.launch(options)
        }
        resourceFilePicker.registerLauncher { mimeTypes ->
            resourceFilePickerLauncher.launch(mimeTypes)
        }
        photoFilePicker.registerLauncher {
            photoFilePickerLauncher.launch("image/*")
        }
        logFileCreator.registerLauncher { fileName ->
            logFileCreatorLauncher.launch(fileName)
        }
        showAppContent()
        requestStartupPermissions()
        if (savedInstanceState == null) {
            val handledDeepLink = handleExternalIntent(intent)
            if (!handledDeepLink) {
                checkAutoConnectOnAppOpen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    override fun onDestroy() {
        vpnPermissionRequester.complete(false)
        vpnPermissionRequester.registerLauncher(null)
        qrCodeScanRequester.completeCameraPermission(false)
        qrCodeScanRequester.completeScan(null)
        qrCodeScanRequester.registerPermissionLauncher(null)
        qrCodeScanRequester.registerScanLauncher(null)
        resourceFilePicker.complete(null)
        resourceFilePicker.registerLauncher(null)
        photoFilePicker.complete(null)
        photoFilePicker.registerLauncher(null)
        logFileCreator.complete(null)
        logFileCreator.registerLauncher(null)
        super.onDestroy()
    }

    private fun requestStartupPermissions() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showAppContent() {
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            !applicationContext.currentSystemUiSnapshot().isDark
        setContent {
            App(
                padding = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues(),
                qrCodeScanner = qrCodeScanRequester::scan,
                resourceFilePicker = resourceFilePicker::pick,
                photoFilePicker = photoFilePicker::pick,
                logFileCreator = logFileCreator::create,
                requestVpnPermission = vpnPermissionRequester::request,
            )
        }
    }

    private fun checkAutoConnectOnAppOpen() {
        val application = application as SkipiApplication
        val state = application.stateStore.state.value
        if (!state.autoConnectOnAppOpen || state.proxyRunning) return
        application.appScope.launch(Dispatchers.IO) {
            val running = runCatching { deepLinkProxyEngine.status(state.runMode, state).running }
                .getOrElse { state.proxyRunning }
            if (running) return@launch

            val resolvedState = state.resolveActiveNetworkConfig(applicationContext)
            if (resolvedState.activeTrafficConfigId != state.activeTrafficConfigId) {
                application.stateStore.update { it.withActiveTrafficConfig(resolvedState.activeTrafficConfigId) }
            }
            val selectedServer = resolvedState.proxyServers.firstOrNull { it.id == resolvedState.selectedProxyServerId }
                ?: return@launch

            when (val result = deepLinkProxyServiceUseCase.start(resolvedState, selectedServer)) {
                is ProxyServiceResult.Success -> {
                    application.stateStore.update { current ->
                        current.copy(
                            proxyRunning = result.proxyRunning,
                            localProxyPort = result.appState?.localProxyPort ?: current.localProxyPort,
                        )
                    }
                }
                is ProxyServiceResult.Failed -> {
                    tipNotifier.showError(error = result.error)
                }
                ProxyServiceResult.MissingServer -> Unit
            }
        }
    }

    private fun handleExternalIntent(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        data.toSkipiDeepLinkOrNull()?.let { link ->
            (application as SkipiApplication).appScope.launch {
                handleSkipiDeepLink(link)
            }
            return true
        }
        if (!data.isSubscriptionInstallConfigUri()) return false
        val config = intent.toSubscriptionInstallConfigOrNull()
        if (config == null) {
            (application as SkipiApplication).appScope.launch {
                tipNotifier.show(getString(R.string.subscription_install_config_invalid))
            }
            return true
        }
        (application as SkipiApplication).appScope.launch {
            runCatching {
                subscriptionInstallConfigUseCase.install(config)
            }.onSuccess { result ->
                tipNotifier.show(
                    subscriptionInstallMessage(
                        result = result,
                        existingUrlTemplate = getString(R.string.subscription_install_existing_url),
                        successTemplate = getString(R.string.proxy_server_list_subscription_update_result),
                        failedTemplate = getString(R.string.proxy_server_list_subscription_update_result_with_failed),
                    ),
                )
            }.onFailure { error ->
                tipNotifier.showError(error, getString(R.string.subscription_install_config_failed))
            }
        }
        return true
    }

    private suspend fun handleSkipiDeepLink(link: SkipiDeepLink) {
        val application = application as SkipiApplication
        when (link) {
            is SkipiDeepLink.Subscription -> {
                runCatching {
                    subscriptionInstallConfigUseCase.install(
                        features.subscription.SubscriptionInstallConfig(
                            name = getString(R.string.skipi_imported_subscription),
                            url = link.url,
                            userAgent = features.subscription.DefaultSubscriptionUserAgent,
                        ),
                    )
                }.onSuccess { result ->
                    tipNotifier.show(
                        subscriptionInstallMessage(
                            result = result,
                            existingUrlTemplate = getString(R.string.subscription_install_existing_url),
                            successTemplate = getString(R.string.proxy_server_list_subscription_update_result),
                            failedTemplate = getString(R.string.proxy_server_list_subscription_update_result_with_failed),
                        ),
                    )
                }.onFailure { error ->
                    tipNotifier.showError(error, getString(R.string.subscription_install_config_failed))
                }
            }

            is SkipiDeepLink.ManualServer -> {
                runCatching {
                    application.stateStore.update { state -> state.withImportedSkipiServer(link.url) }
                }.onSuccess {
                    tipNotifier.show(getString(R.string.skipi_imported_server))
                }.onFailure { error ->
                    tipNotifier.showError(error, getString(R.string.skipi_import_failed))
                }
            }

            is SkipiDeepLink.TrafficConfig -> {
                runCatching {
                    val isHttpUrl = link.content.startsWith("http://", ignoreCase = true) || link.content.startsWith("https://", ignoreCase = true)
                    val content = if (isHttpUrl) {
                        application.subscriptionFetcher.fetch(
                            url = link.content,
                            userAgent = features.subscription.DefaultSubscriptionUserAgent,
                            options = application.stateStore.state.value.toSubscriptionFetchOptions(),
                        )
                    } else {
                        link.content
                    }
                    val sourceUrl = if (isHttpUrl) link.content else link.sourceUrl
                    application.stateStore.update { state ->
                        state.withImportedTrafficConfig(
                            content = content,
                            activate = link.activate,
                            fallbackName = getString(R.string.configs_imported_name),
                            sourceUrl = sourceUrl,
                        )
                    }
                }.onSuccess {
                    tipNotifier.show(
                        getString(
                            if (link.activate) R.string.skipi_imported_config_activated
                            else R.string.skipi_imported_config,
                        ),
                    )
                }.onFailure { error ->
                    tipNotifier.showError(error, getString(R.string.skipi_import_failed))
                }
            }

            SkipiDeepLink.Open -> Unit
            SkipiDeepLink.Close -> runOnUiThread { finishAndRemoveTask() }
            SkipiDeepLink.Connect,
            SkipiDeepLink.Disconnect,
            SkipiDeepLink.Toggle,
            -> handleSkipiProxyControl(link, application)
        }
    }

    private suspend fun handleSkipiProxyControl(
        link: SkipiDeepLink,
        application: SkipiApplication,
    ) {
        val state = application.stateStore.state.value
        val running = runCatching { deepLinkProxyEngine.status(state.runMode, state).running }
            .getOrElse { state.proxyRunning }
        val selectedServer = state.proxyServers.firstOrNull { it.id == state.selectedProxyServerId }
        val result = when (link) {
            SkipiDeepLink.Connect -> if (running) {
                ProxyServiceResult.Success(proxyRunning = true)
            } else {
                deepLinkProxyServiceUseCase.toggle(state.copy(proxyRunning = false), selectedServer)
            }

            SkipiDeepLink.Disconnect -> if (!running) {
                ProxyServiceResult.Success(proxyRunning = false)
            } else {
                deepLinkProxyServiceUseCase.stop(state.runMode)
            }

            SkipiDeepLink.Toggle -> deepLinkProxyServiceUseCase.toggle(
                state.copy(proxyRunning = running),
                selectedServer,
            )

            else -> return
        }
        when (result) {
            is ProxyServiceResult.Success -> application.stateStore.update { current ->
                current.copy(
                    proxyRunning = result.proxyRunning,
                    localProxyPort = result.appState?.localProxyPort ?: current.localProxyPort,
                )
            }

            ProxyServiceResult.MissingServer -> tipNotifier.show(getString(R.string.skipi_control_missing_server))
            is ProxyServiceResult.Failed -> tipNotifier.showError(error = result.error)
        }
    }
}
