---
title: "The JSON editor resets its text only when the value it is given changes, because the desktop server editor passes text from a non-observable object"
author: Radetski
date: 2026-11-24
branch: main
cost: { minutes: 29, tokens: 140814 }
id: l-06f0209e
scope: repo
status: active
src: agent
touches:
  - shared/ui/build.gradle.kts
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/server/editor/SkipiJsonCodeEditor.kt
  - shared/ui/src/desktopTest/kotlin/app/skipi/ui/server/editor/SkipiJsonCodeEditorTypingTest.kt
  - app/src/main/kotlin/app/MainActivity.kt
  - app/src/main/kotlin/app/effects/ProxyStatusSynchronizer.kt
  - app/src/main/kotlin/data/AppSettingsPreferences.kt
  - app/src/main/kotlin/engine/stats/ProxyTrafficStatsService.kt
  - app/src/main/kotlin/engine/vpn/SkipiCoreRuntime.kt
  - app/src/main/kotlin/engine/vpn/SkipiVpnService.kt
---

## Decided
The code editor treats only a changed `value` as an external replacement (Format JSON and similar). A value that merely differs from the editor's own text is a stale echo and is ignored. The desktop server editor keeps the JSON in a plain `var` of the draft server, which Compose does not observe, so after each keystroke the editor was recomposed with the old value and reset the field to it: typing looked impossible.

## Tried and ruled out
- Blaming the audit's change to the editor frame (focus border split out, highlight cache): the new typing test also fails on the pre-audit editor, so it was not the cause.
- Making the draft observable: wider change, and the editor should not depend on the caller for that.

## Watch out
- With a non-observable caller, an external replacement that does not change the value the editor was given (reset to the original text) is not applied.
- Root cause of why typing worked for the user earlier is not established. Not run in the real app, only in Compose UI tests.

In the code: SkipiCodeEditor in shared/ui (seenValue check); tests SkipiJsonCodeEditorTypingTest in shared/ui desktopTest (needs compose.uiTest, added to desktopTest dependencies).
