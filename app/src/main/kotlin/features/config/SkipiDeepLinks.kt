// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import android.net.Uri
import app.AppState
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.config.importTrafficConfigDocument
import features.proxy.server.model.ProxyServer

/** Android owns only conversion from [Uri] to the shared custom-link parser input. */
internal fun Uri.toSkipiDeepLinkOrNull(): SkipiDeepLink? = parseSkipiDeepLinkOrNull(toString())
