---
title: "The desktop session checks the TUN tunnel's health while Core runs, and a ping run probes each server once"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 20, tokens: 106128 }
id: l-cbe7f867
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/tunnel/DesktopTunnelHealthTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopTunnelSession.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/home/DesktopServerLibraryTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/home/DesktopStrategyGroupLatencyTest.kt
  - shared/core/src/jvmMain/kotlin/features/routing/usecase/GeoDatParser.kt
---

## Decided
- A dead TUN tunnel (hev exited) under a live Core was never noticed: the controller's snapshot handles it, but the session only read the snapshot when Core itself stopped. The session now reads it every 3 s while Core runs and no operation is in progress; a failed snapshot also stops Core and clears the intent to run.
- Within one ping run, a server is probed once: groups sharing members, and a member also pinged alone, reuse that answer. Each probe boots a Core instance, so duplicates were real cost. The tester is built per run, so answers never outlive it; a probe that fails or is cancelled is not kept.
- The selected-server config built in composition no longer depends on latency: only whether it builds is read, and latency only orders balancer members. A bulk ping rebuilt it ~6 times a second on the UI thread.
- Refreshing one subscription keeps every other stored server's text as it was (no re-encode of the whole library).

## Watch out
- The test failures around this change were deliberate RED runs before each fix, not flaky tests.
- The backup/reset stop now waits for an operation in flight; it must never be called from inside one, or it waits forever.

In the code: tunnel/DesktopTunnelSession.kt (observeCore, checkDesktopTunnelHealth, stopForBackup, toggle UNDISPATCHED), home/DesktopStrategyGroupLatency.kt (answers map), home/DesktopServerLibrary.kt replaceSubscriptionServers, Main.kt selectedServerConfig keys.
