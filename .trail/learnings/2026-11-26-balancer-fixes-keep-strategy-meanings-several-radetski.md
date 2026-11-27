---
title: Balancer fixes keep strategy meanings; several strategies and settings do less in xray than the app suggests
author: Radetski
date: 2026-11-26
branch: main
cost: { minutes: 13, tokens: 202475 }
id: l-32569561
scope: repo
status: active
src: agent
---

## Decided
Only clear defects in balancer planning were fixed (ranking of failed vs never measured members, head of a capped ordered fallback, duplicate balancers, 15 s default for profile groups). Changing what a strategy means is left for the user to decide.

## Tried and ruled out
- Capping random and round robin groups like latency ones: an existing test pins that they keep every member, so it is a product choice.
- Strict ordered fallback through xray leastLoad cost weights: weights are float32 and overflow after a few members.

## Watch out
Read from xray source, not measured on a device:
- Least load without a burst observatory picks the single lowest delay, the same as least ping.
- Fallback is rendered as least ping with the head of the list as the fallback tag.
- The tolerance setting reaches nothing; probe timeout is ignored and xray uses a fixed 5 s.
- One observatory serves all balancers: first probe URL wins, shortest interval wins.

In the code: shared/app XrayOutboundPlanner, shared/core buildXrayObservatory, xray app/router strategy_leastload.go and app/observatory/observer.go.
