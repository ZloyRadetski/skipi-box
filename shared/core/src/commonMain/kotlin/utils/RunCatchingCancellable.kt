// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package utils

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for code that suspends. The plain one also captures [CancellationException], so a cancelled
 * coroutine goes on running as if the cancellation were an ordinary failure; this one lets it through.
 *
 * Not inline on purpose: an inline function compiled for JVM 21 cannot be inlined into the Android module.
 */
suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}
