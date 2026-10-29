// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

/** Loggable description of a generated config; the config itself carries credentials and can be huge. */
internal fun configFingerprint(configJson: String): String =
    "chars=${configJson.length}, fingerprint=${configJson.hashCode().toUInt().toString(16)}"
