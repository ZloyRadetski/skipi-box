---
title: "Tests in this public repo use example domains, never real server hostnames"
author: Radetski
date: 2026-11-26
branch: main
cost: { minutes: 19, tokens: 111598 }
id: l-cf4a0404
scope: repo
status: active
src: agent
touches:
  - app/src/main/kotlin/features/settings/SettingsPage.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopEscapeHandler.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/tunnel/DesktopVpnSettings.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/settings/DesktopSettingsSearchTest.kt
  - shared/app/src/commonMain/kotlin/app/skipi/app/settings/search/SettingsSearch.kt
  - shared/app/src/commonTest/kotlin/app/skipi/app/settings/search/SettingsSearchTest.kt
  - shared/ui/src/commonMain/composeResources/values-fa/strings.xml
  - shared/ui/src/commonMain/composeResources/values-ru/strings.xml
---

## Decided
Server addresses in tests use reserved example domains (example.com). The repo is public, so a real hostname pushed once stays in git history. The user chose this when a test change was about to introduce a real-looking server host.

## Tried and ruled out
- Pushing the new server hostname as written: it was not yet on the remote and would have become public permanently.

## Watch out
Check test fixtures for real hosts before committing.

In the code: app XrayConfigTest, the direct DNS test for the proxy server domain.
