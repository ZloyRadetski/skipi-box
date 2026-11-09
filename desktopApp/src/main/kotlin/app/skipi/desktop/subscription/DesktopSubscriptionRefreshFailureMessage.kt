// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.subscriptionFailureMessage

/** The shared failure text, recognising Desktop's own HTTP exception. */
internal fun desktopSubscriptionRefreshFailureMessage(error: Throwable): String =
    subscriptionFailureMessage(error) { it is DesktopSubscriptionHttpException }
