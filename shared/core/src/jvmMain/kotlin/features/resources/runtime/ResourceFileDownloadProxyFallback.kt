// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import java.io.IOException

/** Runs a download through a configured proxy, falling back to direct only for I/O failures. */
fun <P> runResourceFileDownloadWithProxyFallback(
    proxy: P?,
    usernameOf: (P) -> String,
    withAuthenticator: (P, () -> Unit) -> Unit,
    onProxyFallback: () -> Unit,
    downloadViaProxy: (P) -> Unit,
    downloadDirect: () -> Unit,
) {
    if (proxy == null) {
        downloadDirect()
        return
    }

    try {
        if (usernameOf(proxy).isBlank()) {
            downloadViaProxy(proxy)
        } else {
            withAuthenticator(proxy) {
                downloadViaProxy(proxy)
            }
        }
        return
    } catch (_: IOException) {
        onProxyFallback()
    }

    downloadDirect()
}
