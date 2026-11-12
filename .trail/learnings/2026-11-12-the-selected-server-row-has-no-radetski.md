---
title: The selected server row has no dot; only its background marks it
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 5, tokens: 13961 }
id: l-960a12ca
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeServerListItem.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyServerCompactListCard.kt
---

## Decided
The selected row in the Home server list is marked only by its raised background, because the user found the accent dot at the right odd. This fits their liking for minimal visuals.

## Tried and ruled out
Keeping the dot or swapping it for a thin accent border: not done, the user asked for the dot to go and did not ask for a replacement.

## Watch out
Not checked on a screen, only compiled. If the selection looks too faint, offer a thin accent border first and do not bring the dot back.

In the code: SkipiProxyServerCompactListCard.kt (selectedSurface).
