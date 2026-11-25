---
title: "Core memory and start time: patch xray geodata only through easily updated patches, never a vendored copy of xray"
author: Radetski
date: 2026-11-25
branch: main
cost: { minutes: 30, tokens: 174861 }
id: l-50bc1937
scope: repo
status: active
src: agent
---

## Decided
The user wants xray-core updates to stay simple, so changes to xray's geodata package must not live in a copied or forked xray tree. Preferred route (awaiting the user's go-ahead): small unified-diff patches applied through Go's -overlay flag.

## Tried and ruled out
- Vendoring xray-core into third_party with a go.mod replace: works, but every xray update becomes a manual re-merge of a 5.7 MB tree. The user rejected it.
- Shrinking the geo inputs (merging CIDRs, dropping domains covered by a parent domain): the bundled lists are already aggregated, so merged counts equal raw counts and redundant domains are under 1 percent. No gain.
- Slim per-config geo files: only saves the file scan, about 19 percent of start time, and does nothing for the peak.
- Removing the GC pause at core start: peak drops from about 168 MB to about 110 MB, but start gets about 10 percent slower.

## Watch out
Measured with a probe against the real geo files: live heap after start is only 5 to 20 MB, the 100+ MB is a transient peak while building the IP sets. Most of it is go4.org/netipx IPSetBuilder (about 136 MB of allocations) fed by loadIP unmarshalling every CIDR. IP building is about 57 percent of start time, domain matchers about 33 percent. The geosite/geoip scan reads the whole file per tag, which matters for the 73 MB RunetFreedom geosite.

In the code: skipi-core internal/runtimetuning PauseGCForStartup; xray-core common/geodata (geodat_loader.go loadIP/seek, ip_matcher.go createFrom). gomobile bind supports -overlay; set GOFLAGS=-overlay=... to cover gomobile and both go build paths.
