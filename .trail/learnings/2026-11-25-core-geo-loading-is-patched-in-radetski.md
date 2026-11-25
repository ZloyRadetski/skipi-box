---
title: "Core geo loading is patched in xray at build time, never forked; recheck patches on every xray update"
author: Radetski
date: 2026-11-25
branch: main
cost: { minutes: 15, tokens: 190624 }
id: l-5c238df7
scope: repo
status: active
replaces: l-50bc1937
src: agent
---

## Decided
Patches to xray's geodata package live in skipi-core and are applied at build time by a small tool, so xray stays upstream and updating it is a version bump plus one check script.

## Tried and ruled out
- Vendored or forked xray copy: every update becomes a manual merge.
- Go -overlay: Go refuses to replace files inside the module cache. A -modfile variant with a replace to a generated patched copy works.
- Slimmer geo inputs (merging CIDRs, dropping covered domains): bundled lists are already aggregated, no gain.

## Watch out
After every xray bump run the check script in skipi-core; an unpatched build still works but is slower and hungrier. Every build path must pass the -modfile.

In the code: skipi-core patches/xray-geodata/README.md, tools/xrayoverlay, scripts/check-xray-patches.sh, desktop Gradle task BuildDesktopSkipiCoreTask.
