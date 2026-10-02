// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import android.content.Context
import app.AppState
import app.ProxyServerState
import app.skipi.app.proxy.ProxyServerTextCopyFormat
import app.skipi.app.proxy.ProxyServerTextCopyResult
import app.skipi.app.proxy.copyProxyServerText
import engine.xray.XrayExportConfigFactory
import features.proxy.server.model.ProxyServer
import features.subscription.DefaultSubscriptionGroupId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utils.formatJsonText

internal sealed interface ProxyServerCopyTextResult {
    data class Success(val text: String) : ProxyServerCopyTextResult
    data object Unsupported : ProxyServerCopyTextResult
    data object InvalidConfig : ProxyServerCopyTextResult
}

internal enum class ProxyServerCopyTextType {
    Url,
    FullJson,
}

internal suspend fun ProxyServerState.proxyServerCopyText(
    context: Context? = null,
    appState: AppState,
): ProxyServerCopyTextResult = copyProxyServerText(server) {
    withContext(Dispatchers.IO) {
        generatedProxyServerXrayConfig(appState, this@proxyServerCopyText).formatJsonText()
    }
}.toAndroidResult()

internal suspend fun ProxyServerState.proxyServerCopyText(
    context: Context? = null,
    appState: AppState,
    type: ProxyServerCopyTextType,
): ProxyServerCopyTextResult {
    val format = when (type) {
        ProxyServerCopyTextType.Url -> ProxyServerTextCopyFormat.Url
        ProxyServerCopyTextType.FullJson -> ProxyServerTextCopyFormat.FullJson
    }
    return copyProxyServerText(server, format) {
        withContext(Dispatchers.IO) {
            generatedProxyServerXrayConfig(appState, this@proxyServerCopyText).formatJsonText()
        }
    }.toAndroidResult()
}

internal suspend fun ProxyServer<*>.proxyServerCopyText(
    context: Context? = null,
    appState: AppState,
    serverId: Int?,
    groupId: Int?,
): ProxyServerCopyTextResult {
    val copyServer = ProxyServerState(
        id = serverId ?: TemporaryCopyServerId,
        server = this,
        groupId = groupId ?: DefaultSubscriptionGroupId,
    )
    return copyServer.proxyServerCopyText(context, appState)
}

private fun generatedProxyServerXrayConfig(
    appState: AppState,
    selectedServer: ProxyServerState,
): String {
    val copyState = appState.withCopyTargetServer(selectedServer)
    return XrayExportConfigFactory.build(copyState, selectedServer)
}

private fun AppState.withCopyTargetServer(target: ProxyServerState): AppState {
    val index = proxyServers.indexOfFirst { server -> server.id == target.id }
    if (index < 0) return copy(proxyServers = proxyServers + target)
    return copy(
        proxyServers = proxyServers.toMutableList().also { servers ->
            servers[index] = target
        },
    )
}

private fun ProxyServerTextCopyResult.toAndroidResult(): ProxyServerCopyTextResult = when (this) {
    is ProxyServerTextCopyResult.Success -> ProxyServerCopyTextResult.Success(text)
    ProxyServerTextCopyResult.Unsupported -> ProxyServerCopyTextResult.Unsupported
    ProxyServerTextCopyResult.InvalidConfig -> ProxyServerCopyTextResult.InvalidConfig
}

private const val TemporaryCopyServerId = -1
