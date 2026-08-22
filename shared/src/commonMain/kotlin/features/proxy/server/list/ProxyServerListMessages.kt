// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import app.shared.res.Res
import app.shared.res.common_copied
import app.shared.res.common_unsupported
import app.shared.res.proxy_server_config_invalid
import app.shared.res.proxy_server_list_all_deleted
import app.shared.res.proxy_server_list_deleted
import app.shared.res.proxy_server_list_duplicates_deleted
import app.shared.res.proxy_server_list_import_result
import app.shared.res.proxy_server_list_invalid_deleted
import app.shared.res.proxy_server_list_joined
import app.shared.res.proxy_server_list_latency_done
import app.shared.res.proxy_server_list_latency_failed
import app.shared.res.proxy_server_list_latency_result
import app.shared.res.proxy_server_list_no_duplicates
import app.shared.res.proxy_server_list_no_invalid
import app.shared.res.proxy_server_list_no_servers_to_delete
import app.shared.res.proxy_server_list_no_subscription_updates
import app.shared.res.proxy_server_list_no_testable
import app.shared.res.proxy_server_list_ping_in_progress
import app.shared.res.proxy_server_list_real_connection_done
import app.shared.res.proxy_server_list_saved
import app.shared.res.proxy_server_list_select_first
import app.shared.res.proxy_server_list_service_restarted
import app.shared.res.proxy_server_list_service_started
import app.shared.res.proxy_server_list_service_stopped
import app.shared.res.proxy_server_list_subscription_update_result
import app.shared.res.proxy_server_list_subscription_update_result_with_failed
import app.shared.res.subscription_install_existing_url

data class ProxyServerListMessages(
    val savedTemplate: String,
    val joinedTemplate: String,
    val deletedTemplate: String,
    val serviceRestarted: String,
    val noTestableServers: String,
    val latencyDoneTemplate: String,
    val realConnectionDoneTemplate: String,
    val subscriptionInstallExistingUrlTemplate: String,
    val subscriptionUpdateResultTemplate: String,
    val subscriptionUpdateResultWithFailedTemplate: String,
    val noSubscriptionUpdates: String,
    val serviceStarted: String,
    val serviceStopped: String,
    val selectServerFirst: String,
    val latencyResultTemplate: String,
    val latencyFailed: String,
    val importResultTemplate: String,
    val copied: String,
    val unsupported: String,
    val configInvalid: String,
    val duplicatesDeletedTemplate: String,
    val noDuplicates: String,
    val invalidServersDeletedTemplate: String,
    val noInvalidServers: String,
    val allServersDeletedTemplate: String,
    val noServersToDelete: String,
    val pingInProgress: String,
)

@Composable
fun proxyServerListMessages(): ProxyServerListMessages {
    return ProxyServerListMessages(
        savedTemplate = stringResource(Res.string.proxy_server_list_saved),
        joinedTemplate = stringResource(Res.string.proxy_server_list_joined),
        deletedTemplate = stringResource(Res.string.proxy_server_list_deleted),
        serviceRestarted = stringResource(Res.string.proxy_server_list_service_restarted),
        noTestableServers = stringResource(Res.string.proxy_server_list_no_testable),
        latencyDoneTemplate = stringResource(Res.string.proxy_server_list_latency_done),
        realConnectionDoneTemplate = stringResource(Res.string.proxy_server_list_real_connection_done),
        subscriptionInstallExistingUrlTemplate = stringResource(Res.string.subscription_install_existing_url),
        subscriptionUpdateResultTemplate = stringResource(Res.string.proxy_server_list_subscription_update_result),
        subscriptionUpdateResultWithFailedTemplate =
            stringResource(Res.string.proxy_server_list_subscription_update_result_with_failed),
        noSubscriptionUpdates = stringResource(Res.string.proxy_server_list_no_subscription_updates),
        serviceStarted = stringResource(Res.string.proxy_server_list_service_started),
        serviceStopped = stringResource(Res.string.proxy_server_list_service_stopped),
        selectServerFirst = stringResource(Res.string.proxy_server_list_select_first),
        latencyResultTemplate = stringResource(Res.string.proxy_server_list_latency_result),
        latencyFailed = stringResource(Res.string.proxy_server_list_latency_failed),
        importResultTemplate = stringResource(Res.string.proxy_server_list_import_result),
        copied = stringResource(Res.string.common_copied),
        unsupported = stringResource(Res.string.common_unsupported),
        configInvalid = stringResource(Res.string.proxy_server_config_invalid),
        duplicatesDeletedTemplate = stringResource(Res.string.proxy_server_list_duplicates_deleted),
        noDuplicates = stringResource(Res.string.proxy_server_list_no_duplicates),
        invalidServersDeletedTemplate = stringResource(Res.string.proxy_server_list_invalid_deleted),
        noInvalidServers = stringResource(Res.string.proxy_server_list_no_invalid),
        allServersDeletedTemplate = stringResource(Res.string.proxy_server_list_all_deleted),
        noServersToDelete = stringResource(Res.string.proxy_server_list_no_servers_to_delete),
        pingInProgress = stringResource(Res.string.proxy_server_list_ping_in_progress),
    )
}
