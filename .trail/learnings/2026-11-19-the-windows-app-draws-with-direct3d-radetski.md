---
title: "The Windows app draws with Direct3D again: both software renderers left black or flashing strips on a fast resize"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 12, tokens: 50796 }
id: l-d9f152ad
scope: repo
status: active
replaces: l-92988a67
src: agent
touches:
  - desktopApp/build.gradle.kts
---

## Decided
The Windows launcher no longer sets -Dskiko.renderApi or -Dsun.java2d.d3d=false: the default (Direct3D) renderer resizes cleanly. Measured on the built image with 10 rounds of 3 s fast SetWindowPos resizing (private bytes): idle 585 MB, peaks 586-641 MB, 586 MB after 10 s, no growth over the rounds (below the 800 MB that was the complaint).

## Tried and ruled out
- SOFTWARE_FAST: after a maximize it kept its old surface size, the rest of the window stayed black.
- SOFTWARE_COMPAT with Skiko's background erase left off: a strip of the new size stays black during a drag. With skiko.rendering.noerasebackground=false the strip takes the AWT background colour but the whole layer is erased on every step, so the interface flashes.
- Disabling the erase only for the layer (SunToolkit.disableBackgroundErase, needs --add-exports java.desktop/sun.awt) and filling only the grown part from a ComponentListener: black stayed and the old picture smeared.
- Colouring only window/rootPane/contentPane: the strip belongs to the layer's native canvas.

## Watch out
- The software renderer idled at about 320-360 MB; Direct3D idles at about 585 MB. That is the price of a clean resize.
- Test with a fast SetWindowPos loop (no sleep) on a consistent image (createDistributable copied to a scratch folder; mixing in a newer desktopApp.jar gives NoSuchMethodError and a blank window), screenshot right after the loop. Find the test window by process path, not by the title SKIPI: that grabbed the user's running app. The first start without the class archive takes about 12 s.

In the code: desktopApp/build.gradle.kts (jvmArgs, isWindowsHost).
