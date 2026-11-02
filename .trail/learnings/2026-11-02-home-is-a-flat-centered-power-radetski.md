---
title: Home is a flat centered power button with a plain list; the user rejected a lightly restyled version of the old layout
author: Radetski
date: 2026-11-02
branch: main
cost: { minutes: 6, tokens: 253474 }
id: l-766abfed
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/home/DesktopProxyHomeLatencyFabTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyServerCompactListCard.kt
---

## Decided
- Home uses a centered power button with status and server name, one quiet line of live facts, and a flat server list with no card surfaces, borders or gradients. On a wide window the button and server details sit in a 300 dp left column and the list fills the right.
- The user picked this concept from three options (single flat list, big centered button, compact panel with a table). A restyle of the old layout was rejected as "not a real rethink", so concept choices should be shown before building.

## Tried and ruled out
- Keeping the hero card and tile grid and only restyling them: rejected as barely different.
- A left control rail card holding the orb and all server details: rejected earlier.

## Watch out
- The wide-layout test clicks the power button at a fixed pixel position; moving the button means moving that click position (now the horizontal centre of a 390 px scene).
- Gradle can keep the distribution folder locked after the app was started from it; stop the Gradle daemon and the running app before createDistributable.

In the code: shared/ui/src/commonMain/kotlin/app/skipi/ui/home (ProxyHomeScreen.kt, SkipiProxyConnectionHero.kt, SkipiProxyServerCompactListCard.kt), desktopApp/src/test/kotlin/app/skipi/desktop/home/DesktopProxyHomeLatencyFabTest.kt
