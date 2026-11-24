// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import platform.LocalProxyXrayConfigOptions

/**
 * Builds the Core config an active [profile] produces through the entry point the runtime uses, so a test of
 * profile behaviour covers the shared planner pipeline and not a parallel one.
 */
internal fun buildProfileConfigGenerated(
    profile: DesktopStoredConfig,
    serverLibrary: DesktopServerLibrary,
    options: LocalProxyXrayConfigOptions,
    configs: List<DesktopStoredConfig> = emptyList(),
    strictFullTunnel: Boolean = false,
    answerTunDns: Boolean = false,
    routing: RoutingConfigRecord = RoutingConfigRecord(),
): DesktopGeneratedXrayConfig = checkNotNull(
    buildDesktopSelectedServerConfigWithBridge(
        serverLibrary = serverLibrary,
        configLibrary = DesktopConfigLibrary(
            selectedConfigId = profile.id,
            configs = listOf(profile) + configs.filterNot { config -> config.id == profile.id },
        ),
        routing = routing,
        options = options,
        trafficConfigContentById = emptyMap(),
        subscriptionPingUrl = "",
        latencyByServerId = emptyMap(),
        strictFullTunnel = strictFullTunnel,
        answerTunDns = answerTunDns,
    ),
) { "No server is selected" }.getOrThrow()

internal fun buildProfileConfig(
    profile: DesktopStoredConfig,
    serverLibrary: DesktopServerLibrary,
    options: LocalProxyXrayConfigOptions,
    configs: List<DesktopStoredConfig> = emptyList(),
    strictFullTunnel: Boolean = false,
): String = buildProfileConfigGenerated(profile, serverLibrary, options, configs, strictFullTunnel).json
