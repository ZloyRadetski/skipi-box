---
title: The balancer switching tolerance setting was removed because xray never read it; other strategy gaps remain
author: Radetski
date: 2026-11-26
branch: main
cost: { minutes: 8, tokens: 39749 }
id: l-22efd98a
scope: repo
status: active
replaces: l-32569561
src: agent
touches:
  - shared/app/src/desktopTest/kotlin/data/backup/BackupFixtureCompatibilityTest.kt
---

## Decided
The user chose to remove the tolerance setting. Old saved groups, backups and profile lines that carry it still load; it is dropped on the next save. Balancer planning fixes so far cover only clear defects; strategy meanings are the user's call.

## Tried and ruled out
- Making tolerance work: it would need a burst observatory and a new design, since leastLoad baselines are absolute delays, not a margin over the best.
- Capping random and round robin groups like latency ones: an existing test pins that they keep every member.
- Strict ordered fallback through xray leastLoad cost weights: float32 weights overflow after a few members.

## Watch out
Read from xray source, not measured on a device:
- Least load without a burst observatory picks the single lowest delay, the same as least ping.
- Fallback is rendered as least ping with the head of the list as the fallback tag.
- Probe timeout is ignored; xray uses a fixed 5 s. One observatory serves all balancers.

In the code: shared/app XrayOutboundPlanner, BackupFixtureCompatibilityTest retired settings, xray app/router strategy_leastload.go.
