// SPDX-License-Identifier: GPL-3.0

package features.config

import utils.decodeFlexibleBase64OrNull

/**
 * Decodes the inline Base64 payload used by SKIPI routing/configuration links.
 *
 * The helper intentionally lives in common code so Android, Desktop and future
 * clients resolve provider-supplied routing profiles with the same wire format.
 */
fun String.decodeSkipiConfigPayloadOrNull(): String? = trim()
    .decodeFlexibleBase64OrNull()
    ?.decodeToString()
    ?.trim()
    ?.takeIf(String::isNotBlank)
