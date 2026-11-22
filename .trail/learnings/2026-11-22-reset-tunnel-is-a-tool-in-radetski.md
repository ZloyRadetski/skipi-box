---
title: "Reset tunnel is a tool in the Home three-dots More submenu, with no confirmation, next to Restart service"
author: Radetski
date: 2026-11-22
branch: main
cost: { minutes: 30, tokens: 131115 }
id: l-c45e07de
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopCoreController.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/core/DesktopCoreSessionTest.kt
---

## Decided
"Reset tunnel" (the full stop of the VPN tunnel and core that Settings > Backup and reset already has) is also offered on Home, in the three-dots menu under More, as one more server tool beside Restart service. Both platforms run the same action their Settings page runs: Android force-shuts the service without waiting for a stuck stop, Desktop stops the tunnel. It asks for no confirmation, like its neighbour Restart service; Settings keeps its dialog.

## Tried and ruled out
Reading "three places" as three separate menus (header, group selector, server editor) or as Android, Desktop and the tray: the user said it is the Home three-dots menu, then More.
A confirmation dialog on Home: left out because stopping the tunnel loses no data and the neighbouring tool has none. Add one if accidental taps become a problem.

## Watch out
Any new entry of the More submenu is a ProxyHomeServerTool: the enum, its title, the Android input set and the Desktop capabilities must all know it, or the menu shows nothing.

In the code: ProxyHomeServerTool.ResetTunnel in ProxyHomeStore.kt, serverToolTitle in ProxyHomeTitles.kt, ProxyServerListPage.kt (Android effect), DesktopProxyHome.kt and Main.kt (Desktop onResetTunnel), DesktopProxyHomeRuntimeToolsTest.
