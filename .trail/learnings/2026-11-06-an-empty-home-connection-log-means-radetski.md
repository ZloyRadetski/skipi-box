---
title: "An empty Home connection log means the Core library lacks the connection-log exports, not a log level problem"
author: Radetski
date: 2026-11-06
branch: main
cost: { minutes: 6, tokens: 68456 }
id: l-a31eb36f
scope: repo
status: active
src: agent
---

## Decided
The Home log showed only app lines because skipicore.dll had no SkipiCoreDesktopReadConnectionLog / SetConnectionLogEnabled: the Kotlin call fails quietly and the poll returns nothing. The Go side was missing from skipi-core's origin/main and every local branch (the earlier learning's Core code never reached the remote), so it was rewritten there (local commit 4120ec8, not pushed at the time).

## Tried and ruled out
Suspecting the log level: Desktop's none handling is right, and Core reads Info dispatcher lines whatever level is set. git pull in skipi-core: remote was already equal to local main.

## Watch out
- After a Core change, rebuild with createDistributable; check the exports with grep ReadConnectionLog desktopApp/build/native/skipi-core/skipicore.h.
- Push skipi-core, or a fresh checkout loses the connection log again.
- Routes whose outbound could not be read show as "default".

In the code: skipi-core connection_log.go, desktop/bridge/bridge.go, cmd/skipicore-desktop/main.go; desktopApp DesktopCoreNative.kt
