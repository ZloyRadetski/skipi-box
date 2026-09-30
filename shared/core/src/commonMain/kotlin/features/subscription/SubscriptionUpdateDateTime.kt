// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

/** Formats a stored subscription update timestamp with the platform's current locale. */
expect fun formatSubscriptionUpdateTimestamp(timestampMillis: Long): String
