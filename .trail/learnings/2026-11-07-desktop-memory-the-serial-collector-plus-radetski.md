---
title: "Desktop memory: the serial collector plus a one-minute heap trim; the Go core and server pings are not the cause"
author: Radetski
date: 2026-11-07
branch: main
cost: { minutes: 39, tokens: 190350 }
id: l-0371d76b
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopHeapTrim.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopHeapTrimTest.kt
---

## Decided
- The desktop JVM runs the serial collector with ShrinkHeapInSteps off. G1 costs ~65 MB of native memory for its own bookkeeping whatever the heap size, serial costs 0.2 MB (~100 MB less resident on an idle app). The app runs a full collection once a minute while 64 MB or more of committed heap is free, which returns a burst's space (225 -> 7 MB in one collection).

## Tried and ruled out
- The Go core as the cause of a 700-900 MB process: its live heap is ~15 MB, Go sys stays ~37 MB after 288 pings at concurrency 32, and idle returns memory. The Home tile shows only live objects, not Go sys.
- Server ping bursts (isolated test instances strip routing and geo data, so they are light). Side finding: each burst of 96 pings leaves ~1.6 MB in the Go heap (~17 KB per ping), a slow leak in skipi-core, too small to explain this.
- Limiting G1 threads (ParallelGCThreads=2, ConcGCThreads=1): its bookkeeping only fell 66 -> 39 MB, serial is cleaner.
- Serial without -XX:-ShrinkHeapInSteps: System.gc() left the heap at 225 MB; it shrinks in steps over several collections.
- Switching Skiko to OpenGL or the software renderer: ~-75 and ~-190 MB private, but it changes how animations and shaders render; not done without the user's say.

## Watch out
- An idle fresh instance is ~340 MB working set, ~450 MB private; ~240 MB of it is the NVIDIA driver loaded by Skiko's Direct3D12 renderer. A connected, long-running process was 746-895 MB with 158 MB of GPU memory against 21 MB fresh; that gap is still unexplained.
- Run-to-run noise is about 80 MB, so compare with JVM Native Memory Tracking (-XX:NativeMemoryTracking=summary, jcmd VM.native_memory), not with working set alone.
- The packaged SKIPI.exe asks for administrator rights, so jcmd from a normal shell cannot attach to it; run the app's jars with the system java in an isolated APPDATA and XDG_RUNTIME_DIR instead, so the single-instance channel of a running app is not used.

In the code: desktopApp/build.gradle.kts (jvmArgs), desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopHeapTrim.kt
