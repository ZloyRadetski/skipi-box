// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import app.BuildConfig

/** False in builds a store keeps up to date (F-Droid): `-PskipiSelfUpdate=false`. */
internal const val SelfUpdateEnabled: Boolean = BuildConfig.SELF_UPDATE
