---
title: "Graphics acceleration is on by default everywhere; the choice is a file of this machine read at the start of main, not an app setting"
author: Radetski
date: 2026-11-20
branch: main
cost: { minutes: 9, tokens: 33207 }
id: l-26c64734
scope: repo
status: active
replaces: l-405d833e
src: agent
touches:
  - desktopApp/build.gradle.kts
---

## Decided
- Default is the graphics card on every desktop system. A weak laptop felt the software renderer (it draws on the processor); it costs more memory (Direct3D idles near 410 MB against about 165 MB) and is one switch away.
- The choice is a tiny on/off file of this machine, read at the first line of main() because Skiko and Java2D pick their renderer when the first window is made; a change applies on the next start. It is not in the app settings, so a backup never carries it to another computer.
- "On" sets no properties (the card's own API); "off" is Skiko's software renderer, and on Windows also Java2D's Direct3D off and the background erase on. The launcher sets no renderer flags: one there would win over the user's choice.
- Desktop only; Android never shows the control.

## Tried and ruled out
- Keeping Windows on the software renderer by default: it was tuned for memory and a clean resize, but it is the likely cause of a slow app on weak laptops.

## Watch out
- Not yet seen on a built app: that properties set in main() still take effect (sun.java2d.d3d is read when AWT starts). The fallback is to relaunch with the flags. Earlier Direct3D showed a white strip on a fast resize and large memory on a big window; check both with the new default.

In the code: desktopApp DesktopGraphicsAcceleration.kt (graphicsAccelerationDefault), desktopApp/build.gradle.kts, DesktopSettingsScreen.kt.
