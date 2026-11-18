---
title: A static memory audit by cheap agents got three things wrong; check them in the code before fixing
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 17, tokens: 68740 }
id: l-3f01ddc4
scope: repo
status: active
src: agent
touches:
  - shared/app/src/desktopTest/kotlin/features/logs/CoreLogMessageLimitTest.kt
  - shared/app/src/jvmMain/kotlin/features/logs/CoreLogRepository.kt
  - shared/app/src/jvmMain/kotlin/features/logs/LogDefaults.kt
---

## Decided
A long log line is shortened to 2000 characters in the in-memory list only; the persisted line stays whole.

## Tried and ruled out
- "Go core has no memory limit": it does, GOGC 50 and a 256 MB soft limit are set in the core's init, so the 21 MB vs 800 MB desktop gap is not explained by the core.
- "Log repository holds 16000 entries": the 16000-slot array holds references only, live entries never exceed 8000.
- Removing the empty graphicsLayer on the background layers and gating the frame loops on visibility: loops already use the frame clock at 25 fps and respect reduce-motion, and the layer isolates invalidation. Left as is until measured.

## Watch out
The desktop memory gap needs a native memory tracking run on a long session, not code edits.

In the code:
- LogDefaults.kt CoreLogMaxMessageChars, CoreLogRepository.kt shortenedForDisplay
- skipicore.go init (GC and memory limit)
