---
title: A Core handle is never freed while a lock-free native call uses it; the desktop crash on stop was not reproduced
author: Radetski
date: 2026-11-22
branch: main
cost: { minutes: 7, tokens: 75278 }
id: l-d104a300
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopCoreController.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/core/DesktopCoreSessionTest.kt
---

## Decided
The occasional desktop crash on a normal tunnel stop is attributed to a use-after-free: stop destroyed the Core session handle while the log poll, the log switch or the TUN health check was inside a native call on it. Those callers now hold the read side of a lock for the native call, and destroying the handle takes the write side. The emergency stop takes no lock on purpose, since it must never wait.

## Tried and ruled out
The forced-termination path (forceStop) was the first suspect, but the desktop app never calls it; it is only reachable through tests. The logs show no failed stops either.

## Watch out
The crash was not reproduced: the fix is covered by a RED/GREEN test against a fake native, not the real library, and no hs_err dump was found. If the crash persists, get hs_err_pid*.log from the JVM working directory; the next suspect is Core's own bounded stop, whose source is outside this repo.

In the code: DesktopCoreController (handleUse lock, destroyHandle), DesktopCoreSessionTest.stopDoesNotDestroyTheHandleWhileTheLogIsBeingRead.
