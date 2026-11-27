// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.repository.ProxyServerRepository
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.configs_imported_name
import app.skipi.ui.resources.skipi_imported_config
import app.skipi.ui.resources.skipi_imported_config_activated
import app.skipi.ui.resources.skipi_imported_server
import app.skipi.ui.resources.skipi_imported_subscription
import features.config.SkipiDeepLink
import features.config.isSingleHttpUrl
import features.config.withImportedSkipiServer
import features.subscription.DefaultSubscriptionUserAgent
import features.subscription.SubscriptionInstallSource
import kotlinx.coroutines.CancellationException

/**
 * Carries out an approved `skipi://add`, `import`, `conf` or `routing` link the way Android does: a
 * subscription is installed, a server goes to the top of the manual servers and is selected, a config is
 * downloaded when the link names an address and then imported. The result is the message for the user.
 */
internal class DesktopLinkImporter(
    private val installSubscription: suspend (DesktopSubscriptionInstallUri) -> Result<String>,
    private val servers: ProxyServerRepository,
    private val fetchConfig: suspend (url: String) -> String,
    private val transformConfigs: ((DesktopConfigLibrary) -> DesktopConfigLibrary) -> Unit,
) {
    suspend fun import(link: SkipiDeepLink): Result<String> = try {
        Result.success(run(link))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun run(link: SkipiDeepLink): String = when (link) {
        is SkipiDeepLink.Subscription -> installSubscription(
            DesktopSubscriptionInstallUri(
                name = desktopText(Res.string.skipi_imported_subscription),
                url = link.url,
                userAgent = DefaultSubscriptionUserAgent,
                source = SubscriptionInstallSource.RawHttp,
            ),
        ).getOrThrow()

        is SkipiDeepLink.ManualServer -> {
            servers.updateCatalog { catalog -> catalog.withImportedSkipiServer(link.url) }
            desktopText(Res.string.skipi_imported_server)
        }

        is SkipiDeepLink.TrafficConfig -> {
            val isUrl = link.content.isSingleHttpUrl()
            val content = if (isUrl) fetchConfig(link.content) else link.content
            val fallbackName = desktopText(Res.string.configs_imported_name)
            transformConfigs { library ->
                DesktopConfigLibraries.importDocument(
                    library = library,
                    content = content,
                    activate = link.activate,
                    fallbackName = fallbackName,
                    sourceUrl = if (isUrl) link.content else link.sourceUrl,
                )
            }
            desktopText(
                if (link.activate) Res.string.skipi_imported_config_activated else Res.string.skipi_imported_config,
            )
        }

        else -> error("This link imports nothing")
    }
}
