// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

/**
 * Keeps SKIPI Core's link merging in step with the settings and the tunnel: Core merges only while a tunnel runs.
 * Only a change reaches Core, so a running session is never disturbed by the same settings again. A Core library
 * without link merging is reported once through [unsupported] and then left alone. Desktop and Android each give it
 * their own way to reach Core.
 */
class MultilinkSync(
    private val apply: (String) -> Result<Unit>,
    private val readStatus: () -> Result<String>,
) {
    private var applied: String? = null

    @Volatile
    var unsupported: Boolean = false
        private set

    fun sync(settings: MultilinkSettings, tunnelRunning: Boolean): Result<Unit> = synchronized(this) {
        if (unsupported) return Result.success(Unit)
        val json = settings.toCoreJson(tunnelRunning)
        if (json == applied) return Result.success(Unit)
        apply(json)
            .onSuccess { applied = json }
            .onFailure { error ->
                // A refused or failed apply leaves Core in a state this class cannot vouch for: the next sync applies afresh.
                applied = null
                if (error is UnsupportedOperationException) unsupported = true
            }
    }

    fun status(): MultilinkStatus {
        if (unsupported) return MultilinkStatus()
        return readStatus().map(::parseMultilinkStatus).getOrElse { error ->
            if (error is UnsupportedOperationException) unsupported = true
            MultilinkStatus()
        }
    }
}

/**
 * How long the tunnel may be down before Core stops merging: a session restart takes a moment, and stopping would
 * throw the measured link speeds away.
 */
const val MultilinkOffDelayMillis = 3_000L
const val MultilinkStatusEveryMillis = 1_000L
