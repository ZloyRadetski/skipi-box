---
title: "Windows TUN needs msys-gcc_s-seh-1.dll shipped next to hev, the official archive omits it"
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 9, tokens: 95109 }
id: l-65e66f29
scope: repo
status: active
src: agent
touches:
  - desktopApp/build.gradle.kts
---

## Decided
Ship msys-gcc_s-seh-1.dll (vendored, SHA-256 checked) with the hev Windows runtime. The official hev-socks5-tunnel-win64.zip has only the exe, msys-2.0.dll and wintun.dll, but the exe imports msys-gcc_s-seh-1.dll.

## Watch out
The process exits at once with code -1073741515 (0xC0000135, DLL not found) on a clean PATH. It runs fine from Git Bash or with MSYS2 on PATH, so it hides on developer machines. Reproduce from PowerShell with PATH limited to system32.

In the code: desktopApp/packaging/windows/msys-gcc_s-seh-1.dll, buildSrc PrepareDesktopHevWindowsRuntimeTask.kt, desktopApp/build.gradle.kts.
