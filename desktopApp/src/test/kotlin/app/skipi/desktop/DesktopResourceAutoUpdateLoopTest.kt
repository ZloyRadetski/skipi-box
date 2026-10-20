// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.runtime.ActiveGeoResourceRefreshTrigger
import features.config.runtime.TrafficConfigRefreshTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopResourceAutoUpdateLoopTest {
    @Test
    fun active_profile_work_runs_while_an_unrelated_periodic_update_is_blocked_and_host_cancel_cleans_up() =
        runBlocking {
            var nowMillis = 100L
            var targets = listOf(target(id = 2))
            val updateStarts = Channel<Int>(Channel.UNLIMITED)
            val profileOneStarted = CompletableDeferred<Unit>()
            val profileOneCancelled = CompletableDeferred<Unit>()
            val releaseProfileOne = CompletableDeferred<Unit>()
            var profileTwoAttempts = 0
            val updater = DesktopResourceAutoUpdater(
                readLatestTargets = { targets },
                isNetworkConnected = { true },
                updateAutomatically = { profileId ->
                    when (profileId) {
                        1 -> {
                            updateStarts.send(profileId)
                            profileOneStarted.complete(Unit)
                            try {
                                releaseProfileOne.await()
                            } catch (cancelled: CancellationException) {
                                profileOneCancelled.complete(Unit)
                                throw cancelled
                            }
                        }
                        2 -> {
                            profileTwoAttempts += 1
                            updateStarts.send(profileId)
                        }
                    }
                },
                nowMillis = { nowMillis },
            )

            // Give profile 2 a future periodic deadline so its later run is the distinct active-profile request.
            updater.runDue()
            assertEquals(2, withTimeout(2_000L) { updateStarts.receive() })
            targets = listOf(target(id = 1), target(id = 2))
            updater.observeTargets(targets)
            updater.observeActiveProfile(trigger(id = 1))

            val wakeups = Channel<Unit>(Channel.CONFLATED)
            val host = launch {
                runDesktopResourceAutoUpdateLoop(
                    updater = updater,
                    wakeups = wakeups,
                    nowMillis = { nowMillis },
                    minimumDelayMillis = 1_000L,
                    idleDelayMillis = 60L * 60L * 1_000L,
                )
            }
            try {
                assertEquals(1, withTimeout(2_000L) { updateStarts.receive() })
                profileOneStarted.await()

                updater.observeActiveProfile(
                    trigger(id = 2).copy(customGeoIpUrl = "https://custom.example/geoip.dat"),
                )
                wakeups.trySend(Unit)

                assertEquals(2, withTimeout(2_000L) { updateStarts.receive() })
                assertEquals(2, profileTwoAttempts)
                assertFalse(releaseProfileOne.isCompleted, "the first profile is still blocked")
                assertFalse(profileOneCancelled.isCompleted, "a distinct profile update must not cancel it")
            } finally {
                host.cancelAndJoin()
            }

            assertTrue(profileOneCancelled.isCompleted, "cancelling the host loop cancels its structured pass")
        }

    private fun target(id: Int) = TrafficConfigRefreshTarget(
        id = id,
        sourceUrl = "",
        autoUpdate = false,
        updateLocked = false,
        updateInterval = "",
        resourceAutoUpdate = true,
        resourceUpdateInterval = "1",
    )

    private fun trigger(id: Int) = ActiveGeoResourceRefreshTrigger(
        profileId = id,
        customGeoIpUrl = "",
        customGeoSiteUrl = "",
    )
}
