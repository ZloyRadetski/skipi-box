// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import java.net.Authenticator

internal actual fun defaultAuthenticatorOrNull(): Authenticator? = Authenticator.getDefault()
