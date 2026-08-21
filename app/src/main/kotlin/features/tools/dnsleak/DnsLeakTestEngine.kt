// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import features.logs.AndroidAppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.SecureRandom

/**
 * Detects which DNS resolvers actually handle the device's queries.
 *
 * A random token is generated, then several unique hostnames under the probe
 * domain are resolved with the system resolver. Because each hostname is
 * unique, they can only be resolved by whatever resolver path the device
 * really uses (the VPN DNS while tunneled). The probe service reports which
 * resolvers fetched the queries; those are compared and classified by
 * [DnsLeakAnalysis.verdict].
 */
internal class DnsLeakTestEngine(
    private val onProgress: (Int) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun run(): DnsLeakTestOutcome = withContext(ioDispatcher) {
        try {
            val token = generateToken()
            resolveProbes(token)
            val records = fetchResults(token)
            val resolvers = DnsLeakAnalysis.resolvers(records)
            DnsLeakTestOutcome(
                resolvers = resolvers,
                verdict = DnsLeakAnalysis.verdict(resolvers),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AndroidAppLogger.warn(LogTag, "DNS leak test failed", error)
            throw error
        }
    }

    /** Resolves [ProbeCount] unique hostnames through the effective DNS path. */
    private suspend fun resolveProbes(token: String) = withContext(ioDispatcher) {
        (1..ProbeCount).map { index ->
            async {
                runCatching {
                    InetAddress.getAllByName("$index.$token.$ProbeDomain")
                }
            }
        }.awaitAll()
        // Success of individual lookups is not required: any query that
        // reaches the probe authoritative server counts for the report.
        onProgress(ProbeCount)
    }

    private suspend fun fetchResults(token: String): List<DnsLeakRecord> {
        // Give the probe service a moment to collect all resolver hits.
        kotlinx.coroutines.delay(ResultSettleDelayMillis)
        var lastError: Throwable? = null
        repeat(ResultFetchAttempts) { attempt ->
            try {
                return fetchResultsOnce(token)
            } catch (error: Throwable) {
                lastError = error
                AndroidAppLogger.warn(
                    LogTag,
                    "Result fetch attempt ${attempt + 1}/$ResultFetchAttempts failed",
                    error,
                )
                if (attempt < ResultFetchAttempts - 1) {
                    kotlinx.coroutines.delay(ResultRetryDelayMillis)
                }
            }
        }
        throw IOException("Could not collect DNS leak results", lastError)
    }

    private fun fetchResultsOnce(token: String): List<DnsLeakRecord> {
        val connection = URL("https://$ProbeDomain/dnsleak/test/$token?json")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            // The result feed rejects requests without a browser-like
            // User-Agent, so always send one.
            connection.setRequestProperty("User-Agent", ResultUserAgent)
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.connect()
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Result feed responded with HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val records = DnsLeakAnalysis.parse(body)
            if (records.isEmpty()) {
                throw IOException("Result feed returned no records")
            }
            return records
        } finally {
            connection.disconnect()
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TokenBytes)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val LogTag = "DnsLeakTest"
        private const val ProbeDomain = "bash.ws"
        internal const val ProbeCount = 10
        private const val TokenBytes = 8
        private const val ResultSettleDelayMillis = 4_000L
        private const val ResultFetchAttempts = 3
        private const val ResultRetryDelayMillis = 2_000L
        private const val ResultUserAgent =
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}

/** Result of one completed DNS leak test run. */
internal data class DnsLeakTestOutcome(
    val resolvers: List<DnsLeakResolver>,
    val verdict: DnsLeakVerdict,
)
