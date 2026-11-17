---
title: "Android's Access logs page is fed from Core's connection log; a local skipicore.aar is used only when provably the required version"
author: Radetski
date: 2026-11-17
branch: main
cost: { minutes: 1, tokens: 11054 }
id: l-a5f2dece
scope: repo
status: active
replaces: l-fda5cc26
src: agent
touches:
  - settings.gradle.kts
---

## Decided
The Access logs page on Android shows Core's connection journal (sniffed host, port, outbound) as plain text, not a tail of access.log, which under a TUN holds bare IPs. It is switched on by the existing access-log setting; no new persisted field. Log pages are monospace plain text, no card per entry.

settings.gradle.kts now accepts ../skipi-core/skipicore.aar only if skipicore.aar.version holds the required version, or the checkout sits exactly on that tag and the AAR is newer than the commit. Otherwise Gradle downloads the GitHub release and caches it.

## Tried and ruled out
A separate "Connection log" toggle: needs a new AppState field in Room, backups and defaults, for no gain over the access-log switch.

## Watch out
Before this, a stale local AAR (Oct 30) shadowed v1.0.32 and lacked SetConnectionLogEnabled/ReadConnectionLog. The required version is read by regex from buildSrc BuildConfig.kt; keep that constant's shape.

In the code: shared/app ConnectionLogReader, features/logs/ConnectionAccessLogFeed, SkipiVpnService.startConnectionAccessLog, settings.gradle.kts localSkipiCoreIsCurrent.
