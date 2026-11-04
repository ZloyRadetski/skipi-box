---
title: "On Windows the physical adapter for Core sockets is chosen by default-route metric plus interface metric, not the interface metric alone"
author: Radetski
date: 2026-11-04
branch: main
cost: { minutes: 19, tokens: 123764 }
id: l-6e43213e
scope: repo
status: active
src: agent
touches:
  - desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/app/DesktopWindowLifecycle.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/settings/DesktopSettingsLibrary.kt
  - desktopApp/src/test/kotlin/app/skipi/desktop/app/DesktopWindowLifecycleTest.kt
---

## Decided
TUN traffic did not flow because Core pinned its own outbound sockets to the wrong adapter. A VPN adapter such as Radmin VPN has interface metric 1 but a default route metric of 9256, so ranking by interface metric chose it over the real Ethernet link. Windows itself adds the two metrics; Core now does the same (default route from the IPv4 forwarding table).

## Tried and ruled out
Blaming hev: run alone against a stub SOCKS server with a temporary Wintun adapter it forwarded TCP correctly. The "socks5 client socket" errors in the TUN log came from hev running out of sockets while upstream connections hung, not from a hev bug.

## Watch out
Core's choice is logged to stderr ("SKIPI TUN: binding Core sockets to interface N"), which does not reach the application log, so a wrong pick is invisible there. The close button now hides to the tray by default, only when a tray exists; the earlier note on the always-shown tray icon still holds.

In the code: skipi-core desktop/netbind (route_table.go, system_windows.go, adapter.go); Core is rebuilt by :desktopApp:buildDesktopCore. Window close default in DesktopWindowLifecycle.kt.
