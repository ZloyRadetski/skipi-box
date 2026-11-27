---
title: "Dialogs and sheets own the Back button while they are shown, and native file dialogs never open on the UI thread"
author: Radetski
date: 2026-11-27
branch: feat/net-merge
cost: { minutes: 29, tokens: 209615 }
id: l-e452a31c
scope: repo
status: active
src: agent
touches:
  - buildSrc/src/main/kotlin/BuildConfig.kt
  - app/src/main/kotlin/engine/network/AndroidLinkMonitor.kt
  - app/src/main/kotlin/engine/vpn/AndroidMultilink.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - shared/core/build.gradle.kts
  - shared/core/src/commonMain/kotlin/features/multilink/MultilinkController.kt
  - shared/core/src/commonMain/kotlin/features/multilink/MultilinkHostLinks.kt
  - shared/core/src/commonMain/kotlin/features/multilink/MultilinkLinksReporter.kt
  - shared/core/src/commonMain/kotlin/features/multilink/MultilinkSettings.kt
  - shared/core/src/commonMain/kotlin/features/multilink/MultilinkSync.kt
---

## Decided
- A dialog or sheet registers its dismiss action with the back registry (BackScope) while it is shown, so Escape and the mouse's back button close it instead of leaving the screen under it. AppWindowDialog, AppWindowBottomSheet and WarningConfirmDialog do it for every caller.
- Leaving the raw config editor saves what Save saves (name, text, source, lock). A draft the save rejects stays open with the reason; leaving again with the same text gives it up.
- The system file dialog (FileDialog) opens on the IO dispatcher. Opened inside a coroutine on the UI dispatcher its modal event loop re-enters Compose's dispatcher and the app shows "Fatal exception in coroutines machinery".
- The speed test measures the line itself when no tunnel runs (direct connection, no proxy); with a tunnel it goes through it. A run lasts about 30 s: the first 2 s of a transfer are left out, the result is the steady part, and the byte budget is only a safety stop.

## Tried and ruled out
- Fixing the mouse back button per screen: both the arrow and the mouse call the same registered action, so the cause was overlays that registered nothing.

## Watch out
- A new overlay that is not built on AppWindowDialog, AppWindowBottomSheet or WarningConfirmDialog must call BackScope itself.
- The Linux StatusNotifier panel draws its own tray menu and ignores the tray icons.

In the code: shared/ui/.../components/NavigationIcon.kt (BackScope), desktopApp/.../backup/DesktopBackupFiles.kt, desktopApp/.../config/DesktopConfigsScreen.kt, shared/app/.../tools/SpeedTestRunner.kt, JvmSpeedTestTransport.kt.
