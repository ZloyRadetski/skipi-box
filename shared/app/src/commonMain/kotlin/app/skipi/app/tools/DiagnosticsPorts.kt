// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

/**
 * Host-owned network access shared by the diagnostic tools. A host decides how a request is
 * routed (through the tunnel's local SOCKS proxy, a bound Android network, or directly); the
 * portable tools never choose a route themselves and never fall back to another one silently.
 */
fun interface DiagnosticsHttpClient {
    /** Returns the body of a successful (HTTP 200) response, or null on any failure. */
    suspend fun getText(url: String, accept: String): String?
}

/** Times one HTTP round trip over the host-chosen route: the delay of the live connection. */
fun interface DiagnosticsLatencyProbe {
    /** Milliseconds until the first answer of a 2xx/3xx response, or null on any failure or timeout. */
    suspend fun measureMillis(url: String, timeoutMillis: Long): Long?
}

/** Sends one TXT query to [server] port 53 and returns the character strings of the first answer. */
fun interface DnsTxtResolver {
    suspend fun queryTxt(server: String, domain: String): List<String>?
}

/** Tunnel-bound transfers used by the speed test; counts bytes as they move. */
interface SpeedTestTransport {
    /** One round trip against [url] (a zero-byte payload); returns the elapsed milliseconds. */
    suspend fun roundTripMillis(url: String): Double

    /** Downloads [url] to the end, reporting each chunk of bytes read. */
    suspend fun download(url: String, onBytes: (Int) -> Unit)

    /** Uploads [totalBytes] random bytes to [url], reporting each chunk written. */
    suspend fun upload(url: String, totalBytes: Long, onBytes: (Int) -> Unit)
}
