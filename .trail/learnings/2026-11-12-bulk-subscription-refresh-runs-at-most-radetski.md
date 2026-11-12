---
title: Bulk subscription refresh runs at most 10 loads at once; the shared refresh was already parallel but unbounded
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 2, tokens: 41738 }
id: l-8c7e579a
scope: repo
status: active
src: agent
touches:
  - shared/app/src/commonMain/kotlin/app/skipi/app/subscription/SubscriptionRefreshUseCase.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/subscription/SubscriptionRefreshUseCaseTest.kt
  - gradlew.bat
  - shared/app/src/commonMain/kotlin/app/skipi/app/subscription/SubscriptionRefreshBatch.kt
---

## Decided
Bulk refresh loads subscriptions in parallel with a cap of 10 at once; the rest wait for a free slot. The cap lives in the shared refresh so Android and Desktop both get it. A failed load frees its slot.

## Tried and ruled out
Nothing sequential was found in the shared path: it already started every load at once with no limit, so with 100 subscriptions all 100 would fire. Any remaining "one after another" feeling is likely the per-subscription coordinator or the server, not checked.

## Watch out
Results must keep request order, and failures must stay isolated to their request.

In the code:
- shared/app/.../subscription/SubscriptionRefreshUseCase.kt: refreshSubscriptions, MAX_CONCURRENT_SUBSCRIPTION_REFRESHES = 10 (Semaphore)
- tests: SubscriptionRefreshUseCaseTest (cap and slot-release cases); run with ./gradlew.bat :shared:app:desktopTest
