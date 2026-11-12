---
title: "Desktop start speed: the runtime gets the JDK archive at build, and each machine writes its own application archive on exit"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 1, tokens: 6439 }
id: l-9157554a
scope: repo
status: active
replaces: l-0e1de187
src: agent
touches:
  - desktopApp/build.gradle.kts
---

## Decided
The jpackage runtime has no class-data-sharing archive, so the build makes the JDK one right after the runtime image (the portable image and the installers both take that runtime). The application archive is made on the user's machine: the launcher runs with -XX:+AutoCreateSharedArchive and the archive next to the jars, written on the first normal exit and read by later starts. Installed starts: 2.4 s and 6 s of CPU before, 1.3 to 1.8 s after.

## Tried and ruled out
A build-time application archive: the installer never carried it (it builds its own image), and the JVM rejects an archive whose jar dates differ; an installer or a zip rewrites dates (two-second local time), so it could not match. Touching the jars alone costs 0.6 s and 1.5 s of CPU. This replaces the earlier decision to train at build time and to rule out AutoCreate.

## Watch out
The archive is written only on a normal exit, so a tray-resident app that is never exited keeps starting without it until then (logoff and shutdown run the exit path). A folder that cannot be written, such as Program Files without elevation, never gets one.
The runtime has no java launcher (made with native commands stripped), so the dump borrows the same-version JDK launcher.
In the code: desktopApp/build.gradle.kts, createRuntimeImage doLast and jvmArgs.
