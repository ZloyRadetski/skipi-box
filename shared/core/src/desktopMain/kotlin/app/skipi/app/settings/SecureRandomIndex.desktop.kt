// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.settings

import java.security.SecureRandom

private val secureRandom = SecureRandom()

actual fun secureRandomIndex(bound: Int): Int = secureRandom.nextInt(bound)
