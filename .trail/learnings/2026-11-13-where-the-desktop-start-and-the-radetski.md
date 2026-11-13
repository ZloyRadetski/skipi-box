---
title: "Where the desktop start and the lag go now: downloads of the geo files at every start, an exit that waits for them, 15 full collections an hour, and 430 MB of…"
author: Radetski
date: 2026-11-13
branch: main
cost: { minutes: 42, tokens: 352707 }
id: l-4dfe98ab
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
---

## Decided
Nothing changed yet; these are the measured causes behind "still slow and laggy" after the class-archive fix, so the next fix starts from numbers instead of guesses. Measured on the packaged 0.5.920 with a copy of the user's data (84 servers, a profile with resource auto-update pointing at an 87 MB geosite.dat and an 18 MB geoip.dat), auto-connect off, isolated APPDATA/LOCALAPPDATA.

- Start with a valid full archive: window at 1.7 s, but 5 s of CPU by then and 9 s by +12 s (three cores busy), private bytes 516 MB, working set 410 MB. Without the archive: window at 13 s, 17 s of CPU. With the 7 MB archive a short-lived link instance leaves behind: window at 3.0 s, 9.4 s CPU.
- Every start downloads the profile's geo files again: GeoResourcePeriodicScheduleState.reconcile makes a new schedule "due immediately", the schedule lives only in memory, and nothing persists when the files were last fetched. 105 MB of network, a second resource-file-download thread pool, and interrupted downloads leave geosite.dat.<random>.tmp files behind (six of them, 67 MB the largest, in the user's profile folder).
- Exit waits for those downloads: after skipi://close the window closes, AWT shuts down, but main stays in application{} runBlocking because the auto-update coroutine is in a blocking read inside withContext(IO) and cancellation does not disconnect it. The process stayed alive >90 s in every test with the user's data; with empty data it exits in seconds. Until it exits the class archive is not written and a relaunch forwards its link to the dying instance.
- Memory before any connection: 433 MB private committed = Java heap 77 + the relocated class archive copy 81 (ArchiveRelocationMode=1 copies the mapped archive) + metaspace 57 + code cache 38 + symbols/other JVM ~25 + Go runtime of skipicore.dll ~60 (measured by loading only the DLL in jshell: +60 MB, +20 threads) + ~95 unattributed native. OpenGL instead of Direct3D changes nothing (532 vs 516 MB), so the NVIDIA driver is not the private-memory cost. Connected, the user's process reaches 680-880 MB private: the Core holding the 87 MB geosite decoded in Go memory is the likely rest.
- Lag while connected: in the user's live process (34 min) the UI thread had 17 s CPU, C2 9 s, 176 young and 15 full serial collections (full pauses ~90 ms each, 1.4 s total), class loading 3.6 s. Idle and connected, Java threads burn ~1.3 s per 20 s (skiko 0.66, UI 0.42, AWT-Windows 0.25) plus ~0.7 s in native threads: about 10% of a core doing nothing visible.

## Tried and ruled out
- On-demand archive dump through jcmd VM.cds dynamic_dump: it works (68 MB in 2.6 s) but needs -XX:+RecordDynamicDumpInfo, and the JVM refuses to start with that flag once the SharedArchiveFile exists and is a dynamic archive, so it cannot sit in a static launcher cfg.
- Blaming the GPU driver for private memory: same numbers with SKIKO_RENDER_API=OPENGL.

## Watch out
- Out-File in Windows PowerShell 5.1 writes UTF-16 unless -Encoding utf8 is given; thread dumps saved that way parse as empty in Python until decoded as UTF-16.
- A second SKIPI start (a link, skipi://close) is a JVM that exits normally: when no valid archive exists it writes its own tiny archive; the main app then loads that valid-but-tiny archive, turns AutoCreateSharedArchive off, and never rewrites it until the next update invalidates it.
- Thread.print on JDK 26 prints nid in decimal, not hex.

In the code:
- desktopApp/.../resources/DesktopResourceAutoUpdater.kt runDue/reconcile; shared/core/.../GeoResourceAutoRefreshScheduling.kt reconcile ("A new schedule is due immediately").
- desktopApp/.../tunnel/DesktopTunnelSession.kt shutDown/waitUntilIdle; Main.kt application{} at L256.
- desktopApp/.../resources/DesktopProfileResourceFiles.kt writeAtomically (temp file deleted only on the normal path).
- Launcher flags in desktopApp/build.gradle.kts; measurement scripts were in the session scratchpad (startprof.ps1, warmrun.ps1, regions.py).
