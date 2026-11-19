---
title: "An unreadable library is copied aside before the app starts empty, and msiexec's quotes are built inside the PowerShell script"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 16, tokens: 106782 }
id: l-e811cd02
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopCoreController.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopUpdateInstallerTest.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopUnreadableLibraryTest.kt
  - shared/core/src/desktopTest/kotlin/features/resources/runtime/ResourceFileDownloadTransferTest.kt
  - shared/core/src/jvmMain/kotlin/features/resources/runtime/ResourceFileDownloadTransfer.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/proxy/DesktopWindowsSystemProxyTest.kt
  - shared/core/src/commonMain/kotlin/features/proxy/server/presentation/ProxyServerPresentation.kt
---

## Decided
- When servers, subscriptions, profiles or settings cannot be read at start, the file is copied to `<name>.unreadable-<millis>` before the app continues empty; the next save would otherwise overwrite the user's data.
- The MSI update passes the path to msiexec with quotes made by `[char]34` inside the script. A double quote inside a `-Command` argument is dropped on its way through the Windows command line (Java does not escape it), so under a user folder with a space msiexec got a cut path and the update silently failed. Checked with a real PowerShell started through ProcessBuilder.
- A resource download that ends before its Content-Length throws an IOException (retried) instead of publishing a short geo file.
- forceClear of the Windows system proxy runs every step and reports the first failure instead of always succeeding.
- Local proxy port fields save 700 ms after typing stops (and on leaving the page), not on every valid prefix; each save asked a running tunnel to reconnect.

## Tried and ruled out
- -EncodedCommand for the update script: works, but changes what the tests read; building the quotes inside the script keeps the plain script.

## Watch out
- Any new PowerShell `-Command` script must not contain a literal double quote; use single-quoted literals or [char]34.

In the code: app/DesktopUnreadableLibrary.kt (orEmptyKeepingUnreadable, used in Main.kt), app/DesktopUpdateInstaller.kt windowsMsiScript, shared/core ResourceFileDownloadTransfer copyToWithProgress, proxy/DesktopWindowsSystemProxy.kt forceClear, settings/DesktopSettingsScreen.kt LocalProxyPortCommitDelayMillis.
