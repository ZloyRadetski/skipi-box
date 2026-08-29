// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

/** Network defaults shared by Android, desktop and future iOS adapters. */
object NetworkDefaults {
    const val IPV4_ANY_ADDRESS = "0.0.0.0"
    const val IPV6_ANY_ADDRESS = "::"
    const val IPV4_LOOPBACK_ADDRESS = "127.0.0.1"
    const val CONNECTIVITY_CHECK_URL = "https://www.gstatic.com/generate_204"
}
