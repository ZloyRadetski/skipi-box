---
title: "The Home connection log shows the newest line at the bottom and follows it with a scroll request, not a scroll"
author: Radetski
date: 2026-11-06
branch: main
cost: { minutes: 6, tokens: 60433 }
id: l-ad454bfe
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/home/SkipiConnectionLogPanelTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiConnectionLogPanel.kt
---

## Decided
The log is laid out in reverse (rows stay newest first, item 0 is the bottom edge), so it reads like a normal log and following it means staying at item 0. Returning to the newest line uses a scroll request, which cannot be cancelled by a wheel animation still running.

## Tried and ruled out
Calling scrollToItem(0) when the pointer leaves: after a wheel scroll it silently did nothing, because the wheel's animated scroll holds a higher priority and cancels the new, lower-priority scroll.

## Watch out
- With reverse layout the wheel direction flips: wheel up (negative delta) goes to older rows. Panel tests that click rows must aim at the bottom of the panel, where the newest row sits.

In the code: shared/ui SkipiConnectionLogPanel.kt (reverseLayout, requestScrollToItem), desktopApp SkipiConnectionLogPanelTest.kt
