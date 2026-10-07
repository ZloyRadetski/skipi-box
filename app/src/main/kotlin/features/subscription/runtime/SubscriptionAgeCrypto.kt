// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

internal typealias SubscriptionAgeCrypto = features.subscription.SubscriptionAgeCrypto

internal object UnsupportedSubscriptionAgeCrypto : SubscriptionAgeCrypto {
    override fun publicKey(secretKey: String): String {
        unsupported()
    }

    override fun decryptArmored(text: String, secretKey: String): String {
        unsupported()
    }

    private fun unsupported(): Nothing {
        error("Age-encrypted subscriptions require Android 8.0 or later")
    }
}
