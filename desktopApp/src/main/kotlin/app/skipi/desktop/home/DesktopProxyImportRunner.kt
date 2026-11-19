// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.store.SharedApplicationAction
import app.skipi.app.store.SharedApplicationActionOutcome
import app.skipi.app.store.SharedApplicationStore
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.desktop_import_changed_profile
import app.skipi.ui.resources.desktop_import_changed_server
import app.skipi.ui.resources.desktop_import_not_saved
import app.skipi.ui.resources.desktop_import_partially_saved
import app.skipi.ui.resources.desktop_import_section_configs
import app.skipi.ui.resources.desktop_import_section_servers
import app.skipi.ui.resources.desktop_import_section_subscriptions
import app.skipi.ui.resources.desktop_import_subscriptions_hint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * Plans and commits a servers/subscriptions/profiles import, saving each library section in turn and
 * reporting which sections were already saved when a later one fails.
 */
internal class DesktopProxyImportRunner(
    private val readSettings: () -> DesktopAppSettings,
    private val readServers: () -> DesktopServerLibrary,
    private val readSubscriptions: () -> DesktopSubscriptionLibrary,
    private val publishSubscriptions: (DesktopSubscriptionLibrary) -> Unit,
    private val readConfigs: () -> DesktopConfigLibrary,
    private val publishConfigs: (DesktopConfigLibrary) -> Unit,
    private val store: SharedApplicationStore,
    private val fetcher: DesktopSubscriptionFetcher,
    private val isTunnelRunning: () -> Boolean,
    private val requestReconnect: (String) -> Unit,
) {
    suspend fun run(input: DesktopProxyImportInput): Result<String> {
        val settings = readSettings()
        val importDeviceHeaders = if (settings.sendDeviceHeaders) {
            DesktopDeviceIdentity.deviceHeaders(settings.installationUuid)
        } else emptyMap()
        val importTimeout = Duration.ofSeconds(
            settings.subscriptionFetchTimeoutSeconds.toLong(),
        )
        // The libraries are read here (their state belongs to the UI thread); parsing what was pasted or picked (up
        // to 8 MB) and fingerprinting every stored server run off it, so a large import does not freeze the window.
        val serversSnapshot = readServers()
        val subscriptionsSnapshot = readSubscriptions()
        val configsSnapshot = readConfigs()
        val plan = try {
            withContext(Dispatchers.Default) {
            DesktopProxyImportPlanner.planWithProviderFetcher(
                input = input,
                existing = DesktopProxyImportExisting(
                    serverFingerprints = serversSnapshot.servers.mapNotNull { stored ->
                        stored.decode().getOrNull()?.connectionFingerprint()
                    }.toSet(),
                    subscriptionUrls = subscriptionsSnapshot.subscriptions.mapTo(mutableSetOf()) { it.url },
                    configContents = configsSnapshot.configs.mapTo(mutableSetOf()) { it.content },
                    serverDuplicatePolicy = DesktopProxyImportDuplicatePolicy.KeepExistingAndRepeated,
                    subscriptionDuplicatePolicy =
                        DesktopProxyImportDuplicatePolicy.KeepExistingDeduplicateRepeated,
                ),
                providerUrlFetcher = { providerUrl ->
                    withContext(Dispatchers.IO) {
                        fetcher.fetch(
                            url = providerUrl,
                            userAgent = "",
                            timeout = importTimeout,
                            proxy = null,
                            deviceHeaders = importDeviceHeaders,
                        ).body
                    }
                },
            )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return Result.failure(error)
        }
        val errors = plan.diagnostics.filter { diagnostic ->
            diagnostic.severity == DesktopProxyImportDiagnosticSeverity.Error
        }
        if (errors.isNotEmpty()) {
            return Result.failure(
                IllegalArgumentException(errors.joinToString(" ") { it.message }),
            )
        }
        val subscriptionsBeforeCommit = readSubscriptions()
        val configsBeforeCommit = readConfigs()
        val committed = withContext(Dispatchers.Default) {
            DesktopProxyImportCommitter.commit(
                plan = plan,
                subscriptionLibrary = subscriptionsBeforeCommit,
                configLibrary = configsBeforeCommit,
            )
        }
        val savedSections = mutableListOf<String>()
        var importedServerSelectionChanged = false
        try {
            if (plan.servers.isNotEmpty()) {
                val selectedServerBefore = readServers().selectedServerId
                val actionResult = store.dispatchAndAwaitInStoreScope(
                    SharedApplicationAction.ImportProxyServerBatch(plan.servers),
                )
                when (val outcome = actionResult.outcome) {
                    SharedApplicationActionOutcome.Completed -> savedSections += desktopText(Res.string.desktop_import_section_servers)
                    is SharedApplicationActionOutcome.Rejected ->
                        throw IllegalStateException(outcome.reason)

                    is SharedApplicationActionOutcome.Failed ->
                        throw IllegalStateException(outcome.reason)
                }
                importedServerSelectionChanged =
                    readServers().selectedServerId != selectedServerBefore
            }
            if (committed.subscriptionLibrary != readSubscriptions()) {
                withContext(Dispatchers.IO) { DesktopSubscriptionLibraries.saveDefault(committed.subscriptionLibrary) }.getOrThrow()
                publishSubscriptions(committed.subscriptionLibrary)
                savedSections += desktopText(Res.string.desktop_import_section_subscriptions)
            }
            if (committed.configLibrary != readConfigs()) {
                val activeBefore = readConfigs().selectedConfigId
                withContext(Dispatchers.IO) { DesktopConfigLibraries.saveDefault(committed.configLibrary) }.getOrThrow()
                publishConfigs(committed.configLibrary)
                savedSections += desktopText(Res.string.desktop_import_section_configs)
                if (isTunnelRunning() && activeBefore != committed.configLibrary.selectedConfigId) {
                    requestReconnect(desktopText(Res.string.desktop_import_changed_profile))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val failure = if (savedSections.isEmpty()) {
                desktopText(Res.string.desktop_import_not_saved, error.message.orEmpty())
            } else {
                desktopText(
                    Res.string.desktop_import_partially_saved,
                    savedSections.joinToString(),
                    error.message.orEmpty(),
                )
            }
            return Result.failure(IllegalStateException(failure, error))
        }
        if (isTunnelRunning() && importedServerSelectionChanged) {
            requestReconnect(desktopText(Res.string.desktop_import_changed_server))
        }
        val diagnostics = plan.diagnostics
            .filter { diagnostic -> diagnostic.severity != DesktopProxyImportDiagnosticSeverity.Error }
            .map(DesktopProxyImportDiagnostic::message)
            .take(2)
            .joinToString(" ")
        val subscriptionHint = if (committed.counts.addedSubscriptions + committed.counts.updatedSubscriptions > 0) {
            " " + desktopText(Res.string.desktop_import_subscriptions_hint)
        } else {
            ""
        }
        return Result.success(
            committed.summary + subscriptionHint +
                diagnostics.takeIf(String::isNotBlank)?.let { " $it" }.orEmpty(),
        )
    }
}
