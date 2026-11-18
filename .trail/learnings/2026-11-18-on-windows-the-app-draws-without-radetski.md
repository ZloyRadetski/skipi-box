---
title: "On Windows the app draws without Direct3D: both Skiko's renderer and Java2D's pipeline held the graphics driver's memory"
author: Radetski
date: 2026-11-18
branch: main
cost: { minutes: 41, tokens: 123852 }
id: l-92988a67
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/home/DesktopStrategyGroupLatency.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/home/DesktopStrategyGroupLatencyTest.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopMemoryTrace.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopMemoryRegionsTest.kt
---

## Decided
The Windows launcher sets Skiko's software renderer and turns off Java2D's own Direct3D pipeline. Measured on an isolated copy with a scripted fast window resize (private bytes, idle / peak / 8 s later): Direct3D 492 / 683 / 573 MB; software renderer with Java2D Direct3D off 321 / 635 / 358 MB.

## Tried and ruled out
- A 32 MB young generation (-Xmn): no change to the resize growth, and the Java heap is about 17 MB live; left out.
- The software renderer alone: 485 / 873 / 596 MB, Java2D's pipeline kept 32 MB write-combined driver blocks (17 of them after a resize).
- OpenGL with Java2D Direct3D off: 480 / 799 / 505 MB, not better.
- Blaming the Go core or the Kotlin heap: JVM native memory tracking committed stayed about 185-190 MB through the resize.

## Watch out
- The remaining resize peak (about +300 MB, back down within seconds) is software raster surfaces; it is freed again, so a quick drag shows it only briefly.
- The software renderer costs CPU on animated backgrounds; the default background is a plain colour, so idle cost is low, but check CPU if an animated style feels slow.
- Method: run the built jars with the system java, an isolated APPDATA, LOCALAPPDATA and XDG_RUNTIME_DIR, move the window with SetWindowPos in a loop, read jcmd VM.native_memory and Sysinternals VMMap (command line writes a csv). The app image under build/compose/binaries/main/app can be older than the MSI: check its date.

In the code:
- desktopApp/build.gradle.kts (jvmArgs, isWindowsHost)
