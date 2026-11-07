// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

enum class DnsLeakVerdict { NoLeak, SuspectedLeak, Unknown }

enum class DnsLeakFailureKind { NoInternet, TunnelNotPassing }

/** What one answering DNS path says on its own: it matches the exit network, differs from it, or cannot be judged. */
enum class DnsLeakResolverStatus { Safe, Leak, Unknown }

/** The phase of a test run in progress: asking the servers and the exit, then locating the networks they exposed. */
enum class DnsLeakStage { Probing, Locating }

/** Platform-neutral verdict and failure classification for DNS probe results. */
object DnsLeakResultPolicy {
    fun verdict(resolverCountries: List<String>, exitCountry: String?): DnsLeakVerdict {
        if (resolverCountries.isEmpty() || exitCountry.isNullOrBlank()) return DnsLeakVerdict.Unknown
        val comparable = resolverCountries.filter(String::isNotBlank)
        if (comparable.isEmpty()) return DnsLeakVerdict.Unknown
        return if (comparable.any { !it.equals(exitCountry, ignoreCase = true) }) {
            DnsLeakVerdict.SuspectedLeak
        } else {
            DnsLeakVerdict.NoLeak
        }
    }

    fun failureKind(hasVpnNetwork: Boolean): DnsLeakFailureKind =
        if (hasVpnNetwork) DnsLeakFailureKind.TunnelNotPassing else DnsLeakFailureKind.NoInternet
}
