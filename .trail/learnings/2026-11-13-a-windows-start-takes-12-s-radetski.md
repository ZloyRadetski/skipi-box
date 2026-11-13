---
title: "A Windows start takes 12 s unless the class archive can be written, so it lives in the user's local app data folder and the app creates that folder"
author: Radetski
date: 2026-11-13
branch: main
cost: { minutes: 46, tokens: 153274 }
id: l-4a32d0b3
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopClassArchive.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopCoreLogs.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopClassArchiveTest.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/anim/DecorativeClock.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/anim/InfinitePulse.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiProxyHeroVisuals.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/anim/DecorativeClockTest.kt
---

## Decided
- The launcher points the class-data archive at the user's local app data folder, and the app creates that folder at the very start. The launcher expands that variable itself; the JVM does not create the folder.
- Measured on the extracted installer: no archive, 12.5 to 14.9 s to the window and ~15 s of CPU, every start; with the archive, 1.4 s and ~3.5 s of CPU, ~24 MB working set when idle.
- Decorative animations (breathing background, connection waves, hero pulse) advance every 40 ms from one clock built on the frame clock's own time, instead of once per display frame. A connected window had been using about a fifth of a core, mostly redrawing.

## Tried and ruled out
- Writing the archive next to the jars: under Program Files a normal user cannot write there, so it was never created and the earlier "1.3 to 1.8 s installed start" never held for those installs.
- Reading time from the system clock in the animation clock: the aura render test drives its own frame time and failed; the clock must follow the frame time.
- Writing the archive from inside the running app: the runtime has no management module, so an on-demand dump needs that module added to the image. Not done.

## Watch out
- The archive is written only on a normal exit (tray Exit, the close link), not when the process is killed or the machine shuts down. After an update the first start is slow again until one normal exit replaces the stale archive.
- jcmd cannot attach to the launcher stub; attach to its child process.

In the code:
- desktopApp/build.gradle.kts (SharedArchiveFile option), desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopClassArchive.kt, Main.kt first line of main
- shared/ui/src/commonMain/kotlin/app/skipi/ui/anim/DecorativeClock.kt, InfinitePulse.kt
