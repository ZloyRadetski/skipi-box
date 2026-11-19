---
title: "A bulk ping's latency batches no longer recompose the whole app or re-project every Home row"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 19, tokens: 88546 }
id: l-05b28830
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/home/DesktopProxyHomeProjectionTest.kt
---

## Decided
- The latency map lives in a State that the root composable never reads in its body; persistence and runtime refresh follow it through snapshotFlow (the map loaded at start is not written back). A read in the body recomposed the whole app on every ~150 ms batch.
- Home projects the library once per library change and lays latencies and running tests over it; rows whose latency did not change keep their object, so the store's equality check and sort/filter cache hold.
- The selected-server config check is built in produceState on Dispatchers.Default (only isSuccess is read; the tunnel builds its own config at connect).
- isRemovedPersistedProxyServer skips the JSON parse when the text never names a removed protocol (it ran for every server on every load and save).

## Watch out
- trail check flags the moved config build as "routing a raw server through the generated path": it is the same call, only off the UI thread.
- Measured with 2000 generated servers (isolated data): window at 0.9 s either way, CPU in the first 10 s 3.9-4.0 -> 3.6 s, ~181 MB private.

In the code: Main.kt latencyByServerIdState, selectedServerConfig produceState; home/DesktopProxyHomeProjection.kt overlayDesktopProxyHomeLatency; home/DesktopProxyHome.kt baseProjection; shared/core ProxyServerPersistenceJson.kt isRemovedPersistedProxyServer.
