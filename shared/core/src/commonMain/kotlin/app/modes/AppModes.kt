// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.modes

const val RunModeVpnService = 0

const val ProxyAppListModeBlacklist = 0
const val ProxyAppListModeWhitelist = 1
const val ProxyAppListModeGlobal = 2


const val ProxyServerListSortDefault = 0
const val ProxyServerListSortName = 1
const val ProxyServerListSortLatency = 2

const val SubscriptionPingModeTcp = 0
const val SubscriptionPingModeHttp = 1

const val ConnectionDisplayModeCompact = 0
const val ConnectionDisplayModeClassic = 1

/** The connection card Home used before the redesign. */
const val ConnectionDisplayModeLegacy = 2

const val BackgroundStyleClassic = 0
const val BackgroundStylePhoto = 1
const val BackgroundStyleConnection = 2
const val BackgroundStyleAurora = 3

fun normalizeBackgroundStyle(style: Int): Int = when (style) {
    BackgroundStylePhoto, BackgroundStyleConnection, BackgroundStyleAurora -> style
    else -> BackgroundStyleClassic
}

