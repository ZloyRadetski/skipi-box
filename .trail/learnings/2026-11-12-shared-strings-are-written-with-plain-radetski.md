---
title: "Shared strings are written with plain quotes; only the Android copy gets escaped quotes, and Desktop texts are resources, not literals"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 9, tokens: 90228 }
id: l-bc0148cd
scope: repo
status: active
src: agent
touches:
  - app/build.gradle.kts
  - buildSrc/src/main/kotlin/AndroidStringEscaping.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopAutostart.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopLogViewer.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopText.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowsAutostart.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/backup/DesktopFullJsonExport.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/config/DesktopConfigsScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/home/DesktopProxyGroups.kt
---

## Decided
Strings in the shared files are written exactly as shown: plain apostrophes and double quotes, no backslash before them. Compose on Desktop prints a backslash literally ("You\'re All Set!" showed up with it). Android resources do need the escapes, so the build copy for Android adds them. Desktop code reads texts through the shared resources too (a small helper for code outside composition); no Russian or English UI literal belongs in Kotlin.

## Tried and ruled out
Copying the shared files to Android untouched (the first version): it worked for the build but would have kept the backslash bug on Desktop. Keeping Android-style escapes in the shared source: Desktop shows them.

## Watch out
A test fails if a shared string contains a backslash-quote. Error detection in the Desktop status line used to match Russian words; it now uses a per-language word list (one resource), so a new failure message needs words that list recognises. Some languages (fa, zh, zh-rCN) lacked about 125 translations and fall back to English until filled.

In the code:
- buildSrc/.../AndroidStringEscaping.kt, used by syncSharedStrings in app/build.gradle.kts
- desktopApp/.../app/DesktopText.kt (desktopText helper)
- shared/ui/src/desktopTest/.../StringResourceSourceTest.kt
