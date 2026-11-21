---
title: "The graphics acceleration choice is a file of this machine read at the start of main, not an app setting, and it replaces the renderer flags in the launcher"
author: Radetski
date: 2026-11-20
branch: main
cost: { minutes: 11, tokens: 91513 }
id: l-405d833e
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopGraphicsAccelerationTest.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/settings/GeneralSettingsCapabilitiesTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopGraphicsAcceleration.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsScreen.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopClassArchiveTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiGeneralSettingsScreen.kt
---

## Decided
- Skiko and Java2D read their renderer once, when the first window is made, so the desktop choice is a tiny on/off file read at the very start of main(); a change applies on the next start. Desktop only (Windows, Linux, macOS): Android never shows the control.
- The choice is not in the app settings: it describes one computer (a weak laptop), and a backup restored on another machine must not carry it.
- Without a choice every system keeps the renderer it had: Windows the software renderer (it was tuned for memory and clean resizing), Linux and macOS Skiko's own default. "On" sets no properties at all (the card's own API); "off" sets Skiko's direct software renderer, and on Windows also Java2D's Direct3D off and the background erase on.
- The renderer -D flags were removed from the Windows launcher: a flag there would win over the user's choice, because the app never replaces a property that is already set.

## Tried and ruled out
- Editing the launcher's .cfg from the app: it sits under Program Files, which a normal user cannot write.
- Keeping the settings in the app's own settings file: it would travel with backups to other machines.

## Watch out
- Not yet shown on a real start: that properties set in main() still take effect, in particular sun.java2d.d3d, which AWT reads when it starts. If an AWT class loads before main() runs the first line, the flag is ignored and the fallback is to relaunch the process with the flags. Measure idle memory and CPU on the built image in both modes before a release.
- Shared strings are plain-quoted and live in five locales (en, ru, zh, zh-rCN, fa); the toggle texts were added to all five.

In the code: desktopApp DesktopGraphicsAcceleration.kt (apply at start, read and write of the file), Main.kt (first line of main), DesktopSettingsScreen.kt (DesktopGeneralSettings), shared/ui SkipiGeneralSettingsScreen.kt (GeneralSettingsControl.GraphicsAcceleration, capability on for Desktop only), desktopApp/build.gradle.kts.
