// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

/**
 * TLS client fingerprints of the bundled Xray core (v26.11.9, transport/internet/tls/tls.go).
 * The core lowercases the name. TLS accepts `unsafe` and `hellogolang`; REALITY refuses both.
 */

/** The presets GUI clients show. `chrome` is Chrome 155+ in this core. */
private val TlsFingerprintPresets = listOf(
    "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized",
)

/** Explicit browser versions the core ships. */
private val TlsFingerprintVersions = listOf(
    "hellochrome_155", "hellochrome_133", "hellochrome_131", "hellochrome_120",
    "hellofirefox_148", "hellofirefox_120",
    "hellosafari_26_3", "helloios_14", "helloios_13", "helloedge_106",
    "hello360_11_0", "helloqq_11_1",
)

/** Names the core accepts but the editor does not offer: more presets and older hello versions. */
private val TlsFingerprintLegacy = setOf(
    "randomizednoalpn",
    "hellorandomized", "hellorandomizedalpn", "hellorandomizednoalpn",
    "hellofirefox_auto", "hellofirefox_55", "hellofirefox_56", "hellofirefox_63", "hellofirefox_65",
    "hellofirefox_99", "hellofirefox_102", "hellofirefox_105",
    "hellochrome_auto", "hellochrome_58", "hellochrome_62", "hellochrome_70", "hellochrome_72",
    "hellochrome_83", "hellochrome_87", "hellochrome_96", "hellochrome_100", "hellochrome_102",
    "hellochrome_106_shuffle", "hellochrome_100_psk", "hellochrome_112_psk_shuf",
    "hellochrome_114_padding_psk_shuf", "hellochrome_115_pq", "hellochrome_115_pq_psk", "hellochrome_120_pq",
    "helloios_auto", "helloios_11_1", "helloios_12_1", "helloandroid_11_okhttp",
    "helloedge_85", "helloedge_auto", "hellosafari_16_0", "hellosafari_auto",
    "hello360_auto", "hello360_7_5", "helloqq_auto",
)

private val TlsFingerprintNotForReality = setOf("unsafe", "hellogolang")

private val TlsFingerprintKnown: Set<String> =
    (TlsFingerprintPresets + TlsFingerprintVersions + TlsFingerprintLegacy + TlsFingerprintNotForReality).toSet()

/** True when the core would build a config with this fingerprint. Blank means the core's default. */
internal fun isKnownTlsFingerprint(name: String, forReality: Boolean): Boolean {
    val normalized = name.trim().lowercase()
    if (normalized.isEmpty()) return true
    if (normalized in TlsFingerprintNotForReality) return !forReality
    return normalized in TlsFingerprintKnown
}

/**
 * The values of the editor's fingerprint list: blank first, then the presets and the explicit versions.
 * A stored value the list does not have (an imported profile) is appended, so the editor shows it.
 */
fun tlsFingerprintEditorOptions(current: String?): List<String> {
    val options = listOf("") + TlsFingerprintPresets + TlsFingerprintVersions
    val stored = current?.trim().orEmpty()
    return if (stored.isEmpty() || stored in options) options else options + stored
}
