---
title: "Startup memory and speed: trim the working set once started and in the background, and let each machine build its own class-data archive"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 26, tokens: 178093 }
id: l-f06ac339
scope: repo
status: active
src: agent
touches:
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/HeaderEasterEgg.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/HeaderEasterEggLogic.kt
  - shared/ui/src/commonTest/kotlin/app/skipi/ui/home/HeaderEasterEggLogicTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWorkingSetTrim.kt
  - desktopApp/build.gradle.kts
---

## Decided
- The process hands its working set back (Windows K32EmptyWorkingSet) once the start has settled, and whenever the window sits in the background (lost focus, minimized, hidden); never while the window is focused. Measured on the installed layout: 348 MB at start, 33 to 55 MB after the trim, and it stays there. Task Manager's Memory column is the working set, which is why it looked like 600 MB.
- The installer had no class-data-sharing archive at all, only the portable image did (a build-time training run that an installer never saw). Installed starts took 2.4 s with 6 s of CPU; with an archive 1.3 to 1.8 s. Now the runtime gets the JDK archive right after it is made, and the launcher uses -XX:+AutoCreateSharedArchive with the archive next to the jars, so the first normal exit on each machine writes the application archive (56 MB) and later starts read it. The build-time training run and its exit hook are removed.

## Tried and ruled out
- Making the application archive at build time and shipping it: an installer rewrites file dates (the cabinet keeps two-second local time, so a build in another time zone never matches), and the JVM rejects an archive whose jar dates differ. Measured: touching the jars costs about 0.6 s and 1.5 s of CPU.
- Running -Xshare:dump with the runtime's own launcher: the runtime is made with the native commands stripped, so it has no java.exe; the dump borrows the JDK launcher of the same version and removes it again.
- The Java heap: after a collection the live data is about 17 MB; the idle memory is native (Direct3D and driver about 170 MB, Core DLL about 60 MB committed, mapped archive).

## Watch out
- The archive is only written on a normal exit, not when the process is killed, and not at all if the install folder is not writable (a non-elevated start under Program Files); the first message of its absence appears once on stdout.
- Choosing Skiko's software renderer would save another 100 to 120 MB but costs CPU on animations; not done, left as a decision.

In the code:
- desktopApp/.../app/DesktopWorkingSetTrim.kt (+ test), wired in Main.kt next to the heap trim
- desktopApp/build.gradle.kts: createRuntimeImage doLast, jvmArgs AutoCreateSharedArchive
