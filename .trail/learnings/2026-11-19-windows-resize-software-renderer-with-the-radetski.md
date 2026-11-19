---
title: "Windows resize: software renderer with the background erase on and a composited window, not Direct3D"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 21, tokens: 34314 }
id: l-c76ee49c
scope: repo
status: active
replaces: l-57169264
src: agent
touches:
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowChrome.kt
---

## Decided
Windows launcher: -Dskiko.renderApi=SOFTWARE_FAST, -Dskiko.rendering.noerasebackground=false, -Dsun.java2d.d3d=false. DesktopWindowChrome paints every AWT layer in the interface colour and adds WS_EX_COMPOSITED to the window (user32 SetWindowLongPtrW). Erase on fills the strip a fast drag has not painted yet with the theme colour; the composited window shows erase and repaint together, so the interface no longer flashes. Measured with a SetWindowPos loop sampling one pixel of the sidebar: without WS_EX_COMPOSITED 97% of samples were the erased background, with it 0.2-0.3%. FAST keeps memory flat: 440-447 MB before and after 4 rounds of resizing a window up to 2500x1400.

## Tried and ruled out
- Direct3D (default renderer): white strip on a fast resize and 1.3 GB in Task Manager on the user's large window.
- SOFTWARE_COMPAT: same look but the heap grew 300-450 MB after a drag (window-sized frames) and came back only after minutes; a System.gc() after the drag did not help.
- Background erase left off: black strip. Erase on without WS_EX_COMPOSITED: theme colour but the interface flashes. Disabling the erase only for the layer (SunToolkit.disableBackgroundErase) and filling the grown part by hand: black stayed.

## Watch out
Test on a consistent image (createDistributable copied to a scratch folder), find the test window by process path (the title SKIPI grabs the user's running app), wait 14 s after the window appears, and sample a pixel during the SetWindowPos loop rather than judging one screenshot.

In the code: desktopApp/build.gradle.kts (jvmArgs), desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowChrome.kt (applyWindowsComposited).
