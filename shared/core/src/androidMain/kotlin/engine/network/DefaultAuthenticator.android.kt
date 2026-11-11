// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import java.net.Authenticator

// Android's java.net does not let the installed authenticator be read back, so nothing is restored on it.
internal actual fun defaultAuthenticatorOrNull(): Authenticator? = null
