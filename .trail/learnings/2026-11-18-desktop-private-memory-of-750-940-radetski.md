---
title: Desktop private memory of 750-940 MB is outside the JVM and the Go core; a temporary trace file finds where
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 15, tokens: 77947 }
id: l-4bdac583
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopMemoryTrace.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopMemoryRegionsTest.kt
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopMemoryTraceTest.kt
---

## Decided
A temporary memory trace writes a block every 30 s to a log in the app's local data folder: process working set and private bytes, JVM pools, threads, the Go core's stats, and a walk of the address space by kind. It is a debug aid and must be switched off before a release.

## Tried and ruled out
- Kotlin heap or Go core as the cause: in one 3-minute run private bytes went 751 -> 943 MB while the JVM heap stayed 44-68 MB, non-heap about 120 MB and Go sys 60 MB, so about 700 MB sits in native memory outside both.
- Reading JVM native memory tracking in-process: the call failed in the installed runtime with an empty ReflectionException although it works on a full JDK; the cause is not yet known.

## Watch out
- Working set falls to 35 MB because of the app's own trim while private bytes stay near 940 MB, so Task Manager's working set hides the real commit.
- Before a release: set the enabled constant to false, remove the native memory tracking JVM option and the jdk.management module from the desktop build file.

In the code:
- DesktopMemoryTrace.kt (DesktopMemoryTraceEnabled, address-space section)
- desktopApp/build.gradle.kts (jvmArgs and modules, marked DEBUG ONLY)
