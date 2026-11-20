---
title: "Security audit of SKIPI Core: macOS hev runs from a hash-checked root copy, and Xray's forced GCs were left for a device measurement"
author: Radetski
date: 2026-11-20
branch: main
cost: { minutes: 54, tokens: 683810 }
id: l-c83bbe61
scope: repo
status: active
src: agent
touches:
  - shared/core/src/jvmMain/kotlin/engine/vpn/AmneziaWgBridgePlan.kt
---

## Decided
- macOS root supervisor: root writes only into /var/run/skipi-hev.<uid>, which it rebuilds every session; the user's run directory holds only tun-stop, which root merely tests. The hev YAML travels base64 inside the script.
- hev is not required to be root-owned in the app bundle: a drag-installed app is owned by the user, so that check would break every normal install. Instead Core hashes the shipped hev at start, root copies it into its state directory and runs the copy only when the SHA-256 matches. This closes the swap window between check and exec.
- Windows: hev must sit inside the Core DLL's own directory (links resolved), runs in a kill-on-close job object, and its temp config gets a protected DACL (SYSTEM, Administrators, owner).
- The AmneziaWG loopback SOCKS asks for credentials. The app's bridge plan generates them (Android needs a SKIPI_CORE_VERSION bump to a Core that understands socksUser/socksPass); StartSession also injects random ones itself when the app sends none.

## Tried and ruled out
- Fighting Xray's start-up GC with our GC pause: common/geodata calls runtime.GC() explicitly about ten times while building matchers, and runtime.GC ignores SetGCPercent(-1). Only a fork without those calls or merging geo rules helps; neither was done because the cost was never measured on a phone.

## Watch out
- An app that sends SOCKS credentials to an older Core breaks AmneziaWG: Xray's socks client offers only user/pass and the old runner answers "no auth". Ship the app change together with the Core release.

In the code: skipi-core internal/hevtun/macsupervisor.go and macflow.go, hevtun_windows.go, internal/hevtun/configpolicy.go, amneziawg_bridge_auth.go; skipi-box shared/core/src/jvmMain/kotlin/engine/vpn/AmneziaWgBridgePlan.kt.
