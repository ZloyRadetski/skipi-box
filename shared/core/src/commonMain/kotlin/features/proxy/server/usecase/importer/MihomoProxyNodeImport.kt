// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.ProxyServer

/** One rejected decoded Mihomo node, without platform logging or UI concerns. */
data class MihomoProxyNodeImportFailure(
    val index: Int,
    val name: String,
    val type: String,
    val reason: String,
    val error: Throwable? = null,
)

/** Result of converting a sequence of decoded Mihomo proxy nodes. */
data class MihomoProxyNodeImportResult(
    val servers: List<ProxyServer<*>>,
    val failures: List<MihomoProxyNodeImportFailure>,
) {
    val rejectedCount: Int get() = failures.size
}

/**
 * Converts decoded Mihomo nodes with the shared server model. The caller can
 * narrow supported protocols for a platform without duplicating validation or
 * rejection classification.
 */
fun List<MihomoYamlMap>.importMihomoProxyNodes(
    startIndex: Int = 0,
    isTypeSupported: (String) -> Boolean = { type -> type.isSupportedMihomoProxyType() },
): MihomoProxyNodeImportResult {
    val servers = mutableListOf<ProxyServer<*>>()
    val failures = mutableListOf<MihomoProxyNodeImportFailure>()
    forEachIndexed { offset, config ->
        val index = startIndex + offset
        val type = config.string("type")?.lowercase().orEmpty()
        val name = config.string("name").orEmpty()
        val rejectionReason = when {
            type.isBlank() -> "missing proxy type"
            !isTypeSupported(type) -> "unsupported proxy type"
            else -> null
        }
        if (rejectionReason != null) {
            failures += MihomoProxyNodeImportFailure(
                index = index,
                name = name,
                type = type,
                reason = rejectionReason,
            )
            return@forEachIndexed
        }

        runCatching { config.toMihomoProxyServer() }
            .onSuccess(servers::add)
            .onFailure { error ->
                failures += MihomoProxyNodeImportFailure(
                    index = index,
                    name = name,
                    type = type,
                    reason = if (error is UnsupportedMihomoProxyException) {
                        error.message.orEmpty()
                    } else {
                        "invalid proxy config"
                    },
                    error = error,
                )
            }
    }
    return MihomoProxyNodeImportResult(
        servers = servers,
        failures = failures,
    )
}
