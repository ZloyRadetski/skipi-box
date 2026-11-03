---
title: On desktop a right mouse click does what a long press does on Android
author: Radetski
date: 2026-11-03
branch: main
cost: { minutes: 3, tokens: 46598 }
id: l-a723f3d5
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/components/SecondaryClick.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigProfileCard.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/config/SkipiTrafficConfigScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyGroupPicker.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyServerCompactListCard.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryPanel.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/subscription/SubscriptionProviderComponents.kt
---

## Decided
Holding the mouse button is not discoverable on desktop, so every place that opens a menu or editor on long press also opens it on right click. One shared modifier does this, and any new long-press action should get it too. Long press by mouse still works.

## Tried and ruled out
Nothing was dropped. The Miuix Card's own long-press parameter cannot be extended, so the modifier is added next to it instead.

## Watch out
The modifier consumes the press in the Initial pass, so it must come before (outside) the tap or long-press handling on the same element. Placed after it, a tap or long press starts first. The mouse behaviour was only compile-checked, not run.

In the code:
- shared/ui/src/commonMain/kotlin/app/skipi/ui/components/SecondaryClick.kt: Modifier.onSecondaryClick
- used in SkipiProxyServerCompactListCard, SkipiProxyGroupPicker, SkipiSubscriptionSummaryPanel, SubscriptionProviderComponents, SkipiTrafficConfigScreen, SkipiTrafficConfigProfileCard
