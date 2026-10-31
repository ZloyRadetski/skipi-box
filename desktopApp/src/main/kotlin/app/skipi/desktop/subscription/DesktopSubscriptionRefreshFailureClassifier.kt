// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.isTransientSubscriptionRefreshFailure
import java.net.http.HttpTimeoutException

/** Supplies Desktop's typed HTTP status and host-specific JDK HTTP timeout classification. */
internal fun isTransientDesktopSubscriptionRefreshFailure(error: Throwable): Boolean =
    isTransientSubscriptionRefreshFailure(
        error = error,
        httpStatusCode = { cause -> (cause as? DesktopSubscriptionHttpException)?.statusCode },
        isHostConnectionFailure = { cause -> cause is HttpTimeoutException },
    )
