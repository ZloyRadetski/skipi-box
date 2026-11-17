---
title: "Android's Access logs page is fed from Core's connection log, and a stale local skipicore.aar shadows the v1.0.32 release"
author: Radetski
date: 2026-11-17
branch: main
cost: { minutes: 15, tokens: 166028 }
id: l-fda5cc26
scope: repo
status: active
src: agent
touches:
  - settings.gradle.kts
  - shared/ui/src/commonMain/kotlin/app/skipi/ui/logs/SkipiLogViewerScreen.kt
---

## Decided
The Access logs page on Android shows Core's own connection journal (sniffed host, port, outbound) as plain text lines, instead of tailing Xray's access.log, which under a TUN only holds bare IPs. It is switched on by the existing "access log" setting, so there is no new persisted field. Log pages are monospace plain text, no card per entry.

## Tried and ruled out
A separate "Connection log" toggle: it needs a new AppState field in Room, backups and defaults in every place that holds it, for no gain over the access-log switch.

## Watch out
../skipi-core/skipicore.aar (built Oct 30) is picked first by settings.gradle.kts and has no SetConnectionLogEnabled/ReadConnectionLog; the v1.0.32 GitHub release has them. Android compiles only against the release, so move the local file away to build.

In the code: shared/app ConnectionLogReader, features/logs/ConnectionAccessLogFeed, app engine/vpn SkipiVpnService.startConnectionAccessLog, shared/ui logs/LogLineText.kt.
