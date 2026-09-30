package features.subscription.runtime

import features.config.ConfigProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionRefreshReconciliationTest {
    @Test
    fun generic_snapshots_preserve_optimistic_concurrency_policy() {
        assertFalse(refreshTargetWasChanged("same", "same"))
        assertTrue(refreshTargetWasChanged("before", "after"))
        assertFalse(subscriptionServerGroupWasChanged(listOf(1, 2), listOf(1, 2)))
        assertTrue(subscriptionServerGroupWasChanged(listOf(1), listOf(2)))
        assertEquals(
            EmbeddedProfileRefreshDecision.APPLY,
            decideEmbeddedProfileRefresh(null, null),
        )
        val baseline = EmbeddedProfileRefreshSnapshot(
            ConfigProfile(
                id = 1,
                name = "Routing",
                content = "[Rule]\nFINAL,PROXY\n",
                sourceUrl = "url",
                updateLocked = false,
                lastUpdatedAtMillis = 1,
            ),
        )
        assertEquals(
            EmbeddedProfileRefreshDecision.CONFLICT,
            decideEmbeddedProfileRefresh(
                baseline,
                baseline.copy(profile = baseline.profile.copy(content = "[Rule]\nFINAL,DIRECT\n")),
            ),
        )
        assertEquals(
            EmbeddedProfileRefreshDecision.LOCKED,
            decideEmbeddedProfileRefresh(
                baseline.copy(profile = baseline.profile.copy(updateLocked = true)),
                baseline.copy(profile = baseline.profile.copy(updateLocked = true)),
            ),
        )
    }
}
