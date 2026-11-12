---
title: "Editing a profile's balancer opens that one group's editor from every entry point, not only Home"
author: Radetski
date: 2026-11-12
branch: main
cost: { minutes: 1, tokens: 1260 }
id: l-74631046
scope: repo
status: active
src: agent
---

## Decided
Every place that lists a profile-owned proxy group (Home list, configs list on Android and Desktop) opens the editor of that single group, because the user wants to edit the group they tapped. Only the "add" button and the profile's own section go to the group list.

## Tried and ruled out
Fixing only Home: the same bug stayed on the configs page because it used a different route, and the user had to report it again. Keeping the old test that expected the configs row to open the group list: it encoded the bug, so it failed once the fix landed.

## Watch out
When a bug is in a flow with several entry points, grep for all callers of the same key or route before calling it fixed, otherwise a sibling screen keeps the bug. The group editor saves on exit, so opening and leaving a profile group rewrites it with defaults (`skipi-display`, `skipi-burst-probe`); this is not fixed.

In the code: `openProfileProxyGroupEditorAtLine` in `TrafficConfigProxyGroupEditor.kt`, `onOpenProxyGroup` in `TrafficConfigPage.kt`, `openGroupLine` in `DesktopTrafficConfigProxyGroupsEditor.kt`; sourced row keys are `source:<configId>:<line>`.
