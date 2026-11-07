---
title: "Desktop memory: serial collector plus a one-minute heap trim; the Go core, pings and driver image sizes are not the cause"
author: Radetski
date: 2026-11-07
branch: main
cost: { minutes: 13, tokens: 195514 }
id: l-c9079c04
scope: repo
status: active
replaces: l-0371d76b
src: agent
touches:
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopHeapTrim.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopHeapTrimTest.kt
---

## Decided
- The desktop JVM runs the serial collector with ShrinkHeapInSteps off. G1 costs ~65 MB of native memory for its own bookkeeping whatever the heap size, serial 0.2 MB (~100 MB less resident when idle). The app runs a full collection once a minute while 64 MB or more of committed heap is free; that returns a burst's space (225 -> 7 MB).

## Tried and ruled out
- The Go core: live heap ~15 MB, Go sys ~37 MB after 288 pings at concurrency 32. The "leak" of ~17-25 KB per ping was a measuring error: probe connections close 1-2 s later and uncollected garbage counts as live. Settled, growth falls to ~40 bytes per probe. Wait ~3 s and GC before measuring a probe.
- Limiting G1 threads: bookkeeping only 66 -> 39 MB.
- Serial without -XX:-ShrinkHeapInSteps: System.gc() leaves the heap at 225 MB.
- "240 MB of NVIDIA driver" as resident memory: it is the summed image size of the loaded driver DLLs, shared with every process using the GPU. Real cost of Direct3D over Skiko's software renderer is ~95 MB working set and ~130 MB private. Software mode still loads ~164 MB of driver images, partly from Java2D's own pipeline (-Dsun.java2d.d3d=false saves ~15-20 MB).

## Watch out
- Idle fresh instance: ~340 MB working set, ~400-450 MB private; a connected long-running process was 746-895 MB with 158 MB GPU memory against 21 MB fresh. That gap is still unexplained.
- Run-to-run noise is up to 80 MB: compare with JVM Native Memory Tracking (-XX:NativeMemoryTracking=summary, jcmd VM.native_memory) and several runs.
- SKIPI.exe asks for administrator rights, so jcmd from a normal shell cannot attach; run the app's jars with the system java and an isolated APPDATA and XDG_RUNTIME_DIR, so a running app's single-instance channel is not touched.

In the code: desktopApp/build.gradle.kts (jvmArgs), desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopHeapTrim.kt
