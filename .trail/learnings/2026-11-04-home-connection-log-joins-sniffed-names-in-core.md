---
title: The Desktop Home connection log joins sniffed names in Core, because access.log only has what the client asked for
author: VQSeGo
date: 2026-11-04
branch: main
id: l-home-connection-log
scope: repo
status: active
src: agent
touches:
  - shared/app/src/commonMain/kotlin/app/skipi/app/home/ConnectionLog.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/SkipiConnectionLogPanel.kt
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/home/ProxyHomeScreen.kt
  - desktopApp/src/main/kotlin/app/skipi/desktop/core/DesktopConnectionLog.kt
---

## Decided
Home shows time, domain and outbound in the left column under the server details, as flat 20 dp rows (user picked the column layout from three options). Pointer over the log holds it, so a row can be read, copied or scrolled to with the wheel; leaving returns to the newest row. Repeats of the newest host and route collapse into a count. It lives in memory only and is switched in Appearance, on by default. Desktop-only: not in the backup, Android supplies no panel.

## Tried and ruled out
Reading access.log: Xray records the address before sniffing, so under TUN every line is a bare IP, whatever routeOnly says. Raising the log level for the dispatcher's Info lines: floods the log viewer and the disk. skipi-core wraps Xray's global log handler instead and reports Info lines to itself only, leaving the configured files untouched.

## Watch out
- Every core.New replaces Xray's process-global log handler. A latency probe used to silence the running session's logs for good; skipi-core now takes the handler back after each one, and any new core.New must do the same.
- The name is joined from dispatcher text ("sniffed domain", "default route for", "taking detour"), so an xray-core update can change it. skipi-core's connection_log_test.go runs real traffic and catches that.
- DesktopCoreController.readConnectionLog must stay unsynchronized: a delay probe holds the monitor for seconds.
- Every Core session counts sequence numbers from zero; DesktopConnectionLog resets its cursor when the number goes down.
- The left column needs a real Linux TUN run to see domains; a plain local run stays disconnected.

In the code: skipi-core connection_log.go and log_handler.go, shared/ui SkipiConnectionLogPanel.kt, desktopApp DesktopConnectionLog.kt
