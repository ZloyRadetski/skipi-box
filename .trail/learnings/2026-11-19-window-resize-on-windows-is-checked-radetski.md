---
title: "Window resize on Windows is checked with a fast SetWindowPos loop on the built image, and the strip is themed by turning Skiko's background erase back on"
author: Radetski
date: 2026-11-19
branch: main
cost: { minutes: 32, tokens: 178354 }
id: l-57169264
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowChrome.kt
---

## Decided
The Windows launcher uses Skiko's SOFTWARE_COMPAT renderer (SOFTWARE_FAST kept its old surface size after a maximize: the rest of the window stayed black) and sets skiko.rendering.noerasebackground=false. Skiko turns the system's background erase off by default, so the strip a fast drag has not painted yet was black; with the erase on, Windows fills it with the AWT background, which DesktopWindowChrome sets recursively (window, root pane, SkiaLayer and its heavyweight canvas) to the interface colour.

## Tried and ruled out
- Colouring only window/rootPane/contentPane: the black strip belongs to the layer's native canvas.
- A slow or single-step resize and a plain maximize in tests: they never showed the bug. It needs a loop of SetWindowPos calls with no sleep, a screenshot right after the loop (mid-drag), and another a couple of seconds later.

## Watch out
- Test against a consistent image: createDistributable, copy build/compose/binaries/main/app/SKIPI to a scratch folder. Swapping only desktopApp.jar into an older image gives NoSuchMethodError in composition and a blank window.
- Find the test window by process path, not by the title SKIPI: that grabbed the user's running app.
- The first start without the class archive takes about 12 s; wait before resizing.
- Memory at a fast resize with SOFTWARE_COMPAT was not measured.

In the code: desktopApp/build.gradle.kts (jvmArgs, isWindowsHost), desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowChrome.kt.
