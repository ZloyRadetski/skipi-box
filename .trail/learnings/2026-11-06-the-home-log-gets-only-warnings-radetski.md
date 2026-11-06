---
title: "The Home log gets only warnings and errors from the app's own log, because the noise was app info lines, not the Xray level"
author: Radetski
date: 2026-11-06
branch: main
cost: { minutes: 19, tokens: 29979 }
id: l-c71ccd8c
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopLogger.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/core/DesktopConnectionLogTest.kt
---

## Decided
Too much junk in the Home log came from SKIPI's own INFO and DEBUG lines sharing the feed with Xray errors. Only Warning and Error app lines reach the Home log now; everything stays in skipi.log. Xray lines still follow the core log level from settings (default error).

## Tried and ruled out
Lowering the Xray log level: it only controls Xray's own lines, so it would not have removed the app lines.

## Watch out
- A new app message that must be visible on Home has to be logged as warn or error.

In the code: desktopApp DesktopLogger.kt (feed filter), DesktopConnectionLogTest.kt
