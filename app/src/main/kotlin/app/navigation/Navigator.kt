// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.navigation

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.json.Json

private val navigationKeyJson = Json { classDiscriminator = "route" }

/**
 * Saves the navigation back stack across Activity recreations (e.g. screen
 * rotation). Every [Route] is @Serializable, so entries are stored as JSON.
 * Entries that cannot be serialized (or decoded back) are dropped instead of
 * crashing; the rest of the stack below them is kept.
 */
val NavigationBackStackSaver: Saver<MutableList<NavKey>, List<String>> =
    object : Saver<MutableList<NavKey>, List<String>> {
        override fun SaverScope.save(value: MutableList<NavKey>): List<String> {
            val encodedKeys = mutableListOf<String>()
            for (key in value) {
                // Entries that cannot be serialized end the saved portion of the
                // stack: anything below them becomes unreachable anyway.
                val encoded = (key as? Route)?.let { route ->
                    runCatching { navigationKeyJson.encodeToString(Route.serializer(), route) }.getOrNull()
                } ?: break
                encodedKeys.add(encoded)
            }
            return encodedKeys
        }

        override fun restore(value: List<String>): MutableList<NavKey> {
            val restoredKeys = mutableListOf<NavKey>()
            for (encoded in value) {
                runCatching { navigationKeyJson.decodeFromString<Route>(encoded) }.getOrNull()
                    ?.let(restoredKeys::add)
            }
            return restoredKeys
        }
    }

/**
 * Simple navigation helper that owns a back stack and result channels.
 * Supports push/replace/pop/popUntil and result APIs: navigateForResult/setResult/observeResult/clearResult.
 */
class Navigator(
    val backStack: MutableList<NavKey>,
) {
    private val resultBus = mutableMapOf<String, MutableSharedFlow<Any>>()

    /**
     * Push a key onto the back stack.
     */
    fun push(key: NavKey) {
        backStack.add(key)
    }

    /**
     * Replace the top key, or push if the stack is empty.
     */
    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    /**
     * Pop the top key if present.
     */
    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    /**
     * Pop until predicate matches the top key.
     */
    fun popUntil(predicate: (NavKey) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /**
     * Navigate expecting a result. Caller should subscribe via observeResult(requestKey).
     */
    fun navigateForResult(route: Route, requestKey: String) {
        ensureChannel(requestKey)
        push(route)
    }

    /**
     * Set a result for the given request and then pop.
     */
    fun <T : Any> setResult(requestKey: String, value: T) {
        ensureChannel(requestKey).tryEmit(value)
        pop()
    }

    /**
     * Observe results for a given request key as a SharedFlow.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> observeResult(requestKey: String): SharedFlow<T> = ensureChannel(requestKey) as SharedFlow<T>

    /**
     * Clear the last emitted result for the request key.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearResult(requestKey: String) {
        ensureChannel(requestKey).resetReplayCache()
    }

    fun current() = backStack.lastOrNull()

    fun backStackSize() = backStack.size

    private fun ensureChannel(key: String): MutableSharedFlow<Any> = resultBus.getOrPut(key) { MutableSharedFlow(replay = 1, extraBufferCapacity = 0) }
}
