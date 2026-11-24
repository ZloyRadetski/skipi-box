---
title: "The connection log names outbounds from the server list, not from Core, because Core only knows tags"
author: Radetski
date: 2026-11-24
branch: main
cost: { minutes: 8, tokens: 98359 }
id: l-812895d8
scope: repo
status: active
src: agent
touches:
  - shared/app/src/commonMain/kotlin/app/skipi/app/home/ConnectionLog.kt
---

## Decided
Core reports the raw Xray tag (proxy, a server id, or <group>-policy-<id> for the balancer member Xray picked). The app turns it into a name with OutboundLabels: another server shows its remarks, a balancer member shows "Group - Server", plain proxy/direct/block stay. Routing colour still uses the raw tag (ConnectionLogEntry.outbound), the shown text is outboundLabel.

## Tried and ruled out
Carrying names through XrayOutboundPlan to the log readers: the plan is built deep in each platform's config factory and never reaches the log code. Tags are deterministic, so ids plus the server list are enough.

## Watch out
- ConnectionLogReader takes labels first and read last so the existing trailing-lambda constructor calls keep working.
- Android freezes the names in VpnServiceStartConfig at start; Desktop reads the current library.

In the code: shared/app home/OutboundLabels.kt, DesktopConnectionLog, SkipiVpnService.startConnectionAccessLog
