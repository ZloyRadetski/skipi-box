package features.subscription.runtime

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
        assertEquals(
            EmbeddedProfileRefreshDecision.CONFLICT,
            decideEmbeddedProfileRefresh(
                EmbeddedProfileRefreshSnapshot("url", false),
                EmbeddedProfileRefreshSnapshot("url", true),
            ),
        )
        assertEquals(
            EmbeddedProfileRefreshDecision.LOCKED,
            decideEmbeddedProfileRefresh(
                EmbeddedProfileRefreshSnapshot("url", true),
                EmbeddedProfileRefreshSnapshot("url", true),
            ),
        )
    }
}
