---
title: Home redesign attempts were rejected and rolled back; agree on a direction with the user before restyling Home again
author: Radetski
date: 2026-11-02
branch: main
cost: { minutes: 20, tokens: 267068 }
id: l-9fdea3a1
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyHomeHeader.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryCard.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionServerPanel.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryPanel.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiSubscriptionSummaryDetails.kt
---

## Decided
- The Home screen stays as committed. Any future redesign starts by agreeing on a direction with the user (a reference screenshot or a described style) before writing code.

## Tried and ruled out
- Decorative accents: an accent dot before the SKIPI title, a pill behind the header buttons, an accent bar in the group selector. The user called them ugly and asked for minimalism.
- A background glow across the whole Home screen. Rejected. Glows inside the connection card were liked at first.
- Wide layout with a fixed left control rail (big power orb, status, selected-server details, latency button) and the server list on the right, replacing the top hero card and the details pane. The user disliked it overall; everything was rolled back.
- Restyling the subscription block. The user asked to leave subscriptions as they are.

## Watch out
- The user prefers minimal visuals and wants subscriptions untouched.
- Do not commit Home design changes until the user explicitly approves them.

In the code: shared/ui/src/commonMain/kotlin/app/skipi/ui/home (ProxyHomeScreen.kt, SkipiProxyConnectionHero.kt, SkipiProxyHomeHeader.kt, SkipiProxyGroupPicker.kt) restored by git checkout
