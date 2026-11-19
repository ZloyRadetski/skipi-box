---
title: "Windows resize: direct software renderer with the background erase on; a composited window flickers at idle"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 8, tokens: 16094 }
id: l-e133e3b0
scope: repo
status: active
replaces: l-c76ee49c
src: agent
touches:
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowChrome.kt
---

## Decided
Windows launcher: -Dskiko.renderApi=SOFTWARE_FAST, -Dskiko.rendering.noerasebackground=false, -Dsun.java2d.d3d=false; DesktopWindowChrome paints every AWT layer in the interface colour. Four combinations measured with a SetWindowPos loop (5 s) sampling a sidebar pixel (blank = erased background, flash) and a pixel near the bottom-right corner (black = unpainted strip), plus 4 s idle sampling:
- FAST, erase off (the old setup): corner black 33%.
- COMPAT, erase off: corner black 0.7%.
- COMPAT, erase on: sidebar blank 97%, corner black 63%.
- FAST, erase on: sidebar blank 0%, corner black 0%, idle black 0%. Chosen. Memory stays flat after resizing a window up to 2500x1400 (about 440 MB before and after).

## Tried and ruled out
- Direct3D: white strip on a fast resize and 1.3 GB in Task Manager on a large window.
- WS_EX_COMPOSITED on the window (SetWindowLongPtrW): it hid the erase flash during a drag but made the window flicker black at idle (about a third of idle samples). Removed.
- SOFTWARE_COMPAT: heap grew 300-450 MB after a drag and came back only after minutes; System.gc() after the drag did not help.
- Disabling the erase only for the layer (SunToolkit.disableBackgroundErase) and filling the grown part by hand: black stayed.

## Watch out
Judge by sampled pixels at idle and during the drag, not one screenshot: the earlier setups looked fine in single frames. Test on a consistent image (createDistributable copied to a scratch folder), find the window by process path (the title SKIPI grabs the user's running app), wait 14 s after the window appears.

In the code: desktopApp/build.gradle.kts (jvmArgs), desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowChrome.kt.
