// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

/**
 * A subscription's own choice about sending the device headers (X-HWID, X-Device-ID, OS and model).
 * Any explicit choice beats the global setting; [Default] leaves the decision to it.
 */
enum class SubscriptionDeviceHeadersMode(val serialized: String) {
    Default(""),
    Enabled("on"),
    Disabled("off"),
    ;

    /** Whether the headers go out, given the global setting. */
    fun resolve(globalEnabled: Boolean): Boolean = when (this) {
        Default -> globalEnabled
        Enabled -> true
        Disabled -> false
    }

    companion object {
        /** Unknown text (an older build kept free text in the neighbouring HWID column) means [Default]. */
        fun fromSerialized(value: String?): SubscriptionDeviceHeadersMode {
            val normalized = value?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.serialized == normalized } ?: Default
        }
    }
}

/** The effective device-headers decision for one subscription. */
fun resolveSubscriptionDeviceHeaders(override: String, globalEnabled: Boolean): Boolean =
    SubscriptionDeviceHeadersMode.fromSerialized(override).resolve(globalEnabled)
