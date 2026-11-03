// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.settings

private const val CredentialAlphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
private const val CredentialLength = 16

/** A cryptographically random integer in `[0, bound)`. */
expect fun secureRandomIndex(bound: Int): Int

/**
 * A random `skipi_` login or password for the local proxy's authentication:
 * 16 characters from 62 is about 95 bits, drawn from a secure generator.
 */
fun generateRandomProxyCredential(
    prefix: String = "skipi_",
    nextIndex: (bound: Int) -> Int = ::secureRandomIndex,
): String {
    val randomPart = buildString(CredentialLength) {
        repeat(CredentialLength) { append(CredentialAlphabet[nextIndex(CredentialAlphabet.length)]) }
    }
    return "$prefix$randomPart"
}
